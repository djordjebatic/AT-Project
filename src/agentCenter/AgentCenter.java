package agentCenter;



import java.util.ArrayList;
import java.util.HashMap;


import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.AccessTimeout;
import javax.ejb.EJB;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import config.ReadConfigUtil;
import rest.nodeRest.NodeRestAPI;
import webSocket.LoggerUtil;
import webSocket.RefreshAgentClasses;
import webSocket.RefreshRunningAgents;
import agent.Agent;
import agent.AID;
import agent.AgentType;



@Startup
@Singleton
@Lock(LockType.READ)
@AccessTimeout(-1)
public class AgentCenter implements AgentCenterAPI
{
	private String alias; //primary key
	private String address; //IP address
	private String masteraddress; //IP addrress of the master node
	private ArrayList<Node> nodes = new ArrayList<Node>();
	ResteasyClient client = new ResteasyClientBuilder().build();
	
	private ArrayList<Agent> agents = new ArrayList<Agent>();
	private HashMap<String,ArrayList<AgentType>> types = new HashMap<String,ArrayList<AgentType>>();
	
	@EJB
	NodeRestAPI rest;
	
	public AgentCenter()
	{
		
	}

	@Override
	@PostConstruct
	public void Init() 
	{
		//load APP INFO from a config file
		 ReadConfigUtil configReader = new ReadConfigUtil();
		 ArrayList<String> params = configReader.getParams();
		 
		 if(params.size() != 3)
		 {
			 System.out.println("PROCESS ABORTED: Agent center terminated - illegal config.csv");
			 System.exit(-1);
		 }
		 alias = params.get(0);
		 address = params.get(1);
		 
		 //if a masterNode, a new empty register of nodes is created
		 if(alias.equals("master"))
		 {
			 masteraddress = address;
			 nodes = new ArrayList<Node>(); 
			 addNode(this); //insert itself to the node list
			 
			 types.put("master", getCreatableAgentTypes());
			 
			 LoggerUtil.log("The {master} node has registered.");

		 }
			 
		 //if a non-masterNode, it contacts the master node to register and get the list of all nodes
		 else
		 {
			 masteraddress = params.get(2);
			 registerAtMasterNode();
			 LoggerUtil.log("A non-master node has registered: {" + alias + "}.");

		 }
		 
	}

	@Override
	@PreDestroy
	public void cleanUp() 
	{
		if(!alias.equals("master"))
		{
			System.out.println("APP INFO: PreDestroy");
			
			//TODO: stop all agents on the node
			for(Agent agent : agents)
			{
				if(agent.getAid().getHost().getAddress().equals(address))
				{
					agent.stop();
				}
			}
			
			//remove the existance of the node
			ResteasyWebTarget target = client.target("http://" + masteraddress +"/AgentTechnology/rest/agentCenter/node/" + alias);
			@SuppressWarnings("unused")
			Response response = target.request().delete();
			System.out.println("APP INFO: Non-master node " + alias + " is shutting down.");
		}
	}

	@Override
	public void registerAtMasterNode() 
	{
        ResteasyWebTarget target = client.target("http://" + masteraddress +"/AgentTechnology/rest/agentCenter/node");
        try
        {
        	types.put(alias,getCreatableAgentTypes());
            Response response = target.request(MediaType.APPLICATION_JSON).post(Entity.entity(this, MediaType.APPLICATION_JSON));
            AgentCenter temp = response.readEntity(new GenericType<AgentCenter>() {});
            this.nodes = temp.getNodes();
            this.agents = temp.getAgents();
            this.types = temp.getTypes();
            System.out.println("APP INFO: Non-master node recived the list of all nodes from master. Size: " + nodes.size());    	
        }
        catch(Exception e)
        {
        	LoggerUtil.log("PROCESS ABORTED: The new agent center cannot connect to the cluster.");
        }
    }

	@Override
	@Schedule(hour="*", minute="*", second="*/25", persistent = false)
	public synchronized void heartbratProtocol()
	{
		if(!alias.equals("master"))
			return; 
		
		for(Node n : this.getNodes())
		{
			if(n.getAlias().equals(this.alias))
				continue;
			
			ResteasyWebTarget target = client.target("http://" + n.getAddress() +"/AgentTechnology/rest/agentCenter/node");
			
			System.out.println("APP INFO: Heartbeat check by: " + alias + " for: " + n.getAlias() + ".");
			try
			{
				Response response = target.request().get();
				if(response.getStatus()!=200)
				{
					throw new Exception();
				}
			}
			catch(Exception e)
			{
				try
				{
					if(!alias.equals("master"))
					{
						System.out.println("APP INFO: Salje se na: " + masteraddress);
						ResteasyWebTarget target2 = client.target("http://" + masteraddress +"/AgentTechnology/rest/agentCenter/node/" + n.getAlias());
						Response response = target2.request().delete();
						LoggerUtil.log("Node {" + n.getAlias() + "} is being shut down because it is not responding according to the heartbeat protocol.");				
						System.out.println("APP INFO: Delation returned status: " + response.getStatus());
						
						
						//TODO: dodaj sve tipove kada je cvor master
						
					}
					else
					{
						
						LoggerUtil.log("Node {" + n.getAlias() + "} is not responding to the heartbeat protocol.");
						Node toBeDeleted = findNode(n.getAlias());
						
						deleteFromAllNodes(toBeDeleted);
						deleteNode(toBeDeleted);					
						System.out.println("APP INFO: Deleted from master node.");
					}
				}
				catch(Exception e2)
				{
					System.out.println("APP INFO: Delation error for: " + n.getAlias());
				}	
			}

		}

	}

	@Override
	public void  informOtherNodes(AgentCenter newNode) 
	{
		if(!alias.equals("master"))
		{
			LoggerUtil.log("PROCESS ABORTED: Only the master node can inform other nodes about a new member.");
			return;
		}
		
		ResteasyClient client = new ResteasyClientBuilder().build();
		int counter = 0;
		
		for(Node n : nodes)
		{
			if(n.getAlias().equals("master") || n.getAlias().equals(newNode.alias))
				continue;
			
			ResteasyWebTarget target = client.target("http://" + n.getAddress() +"/AgentTechnology/rest/agentCenter/node");
			try
			{
	            Response response = target.request(MediaType.APPLICATION_JSON).post(Entity.entity(newNode, MediaType.APPLICATION_JSON));
	            if(response.readEntity(Boolean.class) == true)
	            {
	            	counter++;
	                System.out.println("APP INFO: " + n.getAlias() + " node has been informed about: " + newNode.alias + ".");    	
	            }
	            else
	            {
	            	throw(new Exception());
	            }	
			}
			catch(Exception e)
			{
	        	System.out.println("PROCESS ABORTED: Unable to inform " + n.getAlias() + " node about a new member.");
			}	
		}
        System.out.println("APP INFO: " + counter + " nodes were informed about the new member: " + newNode.address + ".");    	
	}

	@Override
	public void deleteFromAllNodes(Node toBeDeleted) 
	{
		if(!alias.equals("master"))
		{
        	System.out.println("PROCESS ABORTED: Only the master node can delete from other nodes");
			return;
		}
		
		for(Node n : nodes)
		{
			if(n.getAlias().equals("master") || n.getAlias().equals(toBeDeleted.getAlias()))
				continue;
			
			ResteasyWebTarget target = client.target("http://" + n.getAddress() +"/AgentTechnology/rest/agentCenter/node/" + toBeDeleted.getAlias());
			try
			{
	            Response response = target.request().delete();
	            if(response.getStatus() == 404)
	            {
	            	throw(new Exception());
	            }
	            else
	            {
	                System.out.println("APP INFO: " + toBeDeleted.getAlias() + " node has been deleted from: " + n.getAlias() + ".");    	
	            }
			}
			catch(Exception e)
			{
	        	System.out.println("PROCESS ABORTED: Unable to delete from " + n.getAlias() + ".");
			}
		}
		
	}

	@Override
	public void handleMessage()
	{
		
	}
	
	
	// * * * GET & SET * * * 

	@Override
	public String getAlias() {
		return new String(alias);
	}

	@Override
	@Lock(LockType.WRITE)
	public void setAlias(String alias) {
		this.alias = alias;
	}

	@Override
	public String getAddress() {
		return new String(address);
	}

	@Override
	@Lock(LockType.WRITE)
	public void setAddress(String address) {
		this.address = address;
	}

	@Override
	@Lock(LockType.WRITE)
	public ArrayList<Node> getNodes() 
	{
		ArrayList<Node> l = new ArrayList<Node>();
		
		for(Node n : nodes) 
		{
			l.add(new Node(n));
		}
		return l;
	}

	@Override
	@Lock(LockType.WRITE)
	public void setNodes(ArrayList<Node> imenik) {
		this.nodes = imenik;
	}	

	@Override
	public String getMasteraddress() {
		return new String(masteraddress);
	}

	@Override
	@Lock(LockType.WRITE)
	public void setMasteraddress(String masteraddress) {
		this.masteraddress = masteraddress;
	}

	@Override
	public ArrayList<Agent> getAgents()
	{
		ArrayList<Agent> retVal = new ArrayList<Agent>();
		
		for(Agent a : agents)
		{
			retVal.add(new Agent(a));
		}
		
		return retVal;
	}

	@Override
	@Lock(LockType.WRITE)
	public void setAgents(ArrayList<Agent> agents) 
	{
		this.agents = agents;
	}

	@Override
	public HashMap<String,ArrayList<AgentType>> getTypes()
	{
		return types;
	}

	@Override
	@Lock(LockType.WRITE)
	public void setTypes(HashMap<String,ArrayList<AgentType>> types)
	{
		this.types = types;
	}
	
	
	// * * * NODES LIST FUNCTIONS * * *

	@Override
	@Lock(LockType.WRITE)
	public void addNode(AgentCenter a)
	{
		nodes.add(new Node(a.alias,a.address));
		LoggerUtil.log("Node {" + a.getAlias() + "} joined the cluster.");
	}

	@Override
	public Node findNode(String alias) 
	{
		for(Node n : nodes)
		{
			if(n.getAlias().equals(alias))
			{
				return n;
			}
		}
		return null;
	}

	@Override
	@Lock(LockType.WRITE)
	public void deleteNode(Node n)
	{
		//remove agent types from hashmap
		removeNodeTypes(n.getAlias());
		
		//remove agents running on the server being shut down from the running agent list
		for(Agent agent : new ArrayList<Agent>(agents))
		{
			if(agent.getAid().getHost().getAddress().equals(n.getAddress()))
			{
				agents.remove(agent);
			}
		}
		
		//remove the node finally
		nodes.remove(n);
		
		RefreshAgentClasses.refresh(types.values());
		RefreshRunningAgents.refresh(getAgents());
		LoggerUtil.log("Node {" + n.getAlias() +"} was removed from the cluster." );
	}

	// * * * AGENT LIST FUNCTIONS * * *

	@Lock(LockType.WRITE)
	@Override
	public void addAgent(Agent agent)
	{
		agents.add(agent);
		LoggerUtil.log("Agent [" + agent.getAid().getName() + " - " + agent.getAid().getType().getName() + "] was started at {" + agent.getAid().getHost().getAlias() + "}.");
		RefreshRunningAgents.refresh(getAgents());
	}
	
	@Override
	public Agent findAgent(AID aid)
	{
		for(Agent a : agents)
		{
			if(a.getAid().getName().equals(aid.getName()))
				if(a.getAid().getHost().getAddress().equals(aid.getHost().getAddress()))
					if(a.getAid().getType().getName().equals(aid.getType().getName()))
							return a;
		}
		return null;
	}
	

	@Lock(LockType.WRITE)
	@Override
	public boolean removeRunningAgent(String type, String name)
	{
		Agent temp = null;
		
		for(Agent a : agents)
		{
			if(a.getAid().getName().equals(name) && a.getAid().getType().getName().equals(type))
			{				
				//an agent is stopped by its host
				if(a.getAid().getHost().getAddress().equals(this.address))
				{
					a.stop();
				}	
					
				temp = a;
			}
		}
		if(temp!=null)
		{
			agents.remove(temp);
			RefreshRunningAgents.refresh(getAgents());
			LoggerUtil.log("Agent [" + temp.getAid().getName() + " - " + temp.getAid().getType().getName() + "] was removed from {" + temp.getAid().getHost().getAlias() + "}.");
			return true;
			
		}
		
		
		
		return false;
	}


	// * * * AGENT TYPE HASHMAP FUNCTIONS * * *
	@Lock(LockType.WRITE)
	@Override
	public void addType(ArrayList<AgentType> list, String name)
	{
		types.put(name, list);
		RefreshAgentClasses.refresh(types.values());
	}
	

	
	@Override
	public ArrayList<AgentType> getCreatableAgentTypes()
	{
		ArrayList<AgentType> retVal = new ArrayList<AgentType>();
		
		try
		{
			InitialContext ctx = new InitialContext();
			NamingEnumeration<NameClassPair> list = ctx.list("java:module");
			
			while (list.hasMore()) 
			{
				String wtf = list.next().getName();
				
				if (wtf.contains("!instantiableAgents"))
				{
					retVal.add(new AgentType(wtf.split("!")[0], "instantiableAgents"));
					System.out.println("APP INFO: found agent type: " + wtf.split("!")[0]);
				}
					
			}

		}
		catch (Exception e) 
		{
			e.printStackTrace();
		}
		return retVal;
	}
	
	//returns the first node which can build an agentType
	@Override
	public Node findNodeWithAgentType(String type)
	{
		ArrayList<ArrayList<AgentType>> values = new ArrayList<ArrayList<AgentType>>(types.values());
		ArrayList<String> nodeNames = new ArrayList<String>(types.keySet());
				
		for(int i = 0 ; i < values.size() ; i ++ )
		{
			ArrayList<AgentType> temp = values.get(i);
			for(AgentType at : temp)
			{
				if(at.getName().equals(type))
				{
					return findNode(nodeNames.get(i));
				}
			}
		}
		
		return null;
	}
	
	@Override
	@Lock(LockType.WRITE)
	public void removeNodeTypes(String a)
	{
		types.remove(a);
		System.out.println("* * * BRISANJE TIPOVA: " + a + " na cvoru: " + this.alias);
		RefreshAgentClasses.refresh(types.values());

	}
	
	
	
}
