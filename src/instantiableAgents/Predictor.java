package instantiableAgents;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import javax.ejb.Stateful;

import agent.AID;
import agent.Agent;
import jms.JMSQueue;
import locatorUtils.Match;
import message.ACLMessage;
import message.Performative;
import webSocket.LoggerUtil;
import webSocket.dto.MatchesDTO;

@SuppressWarnings("serial")
@Stateful
public class Predictor extends Agent implements Serializable
{
	
    public MatchesDTO list = new MatchesDTO();	
    Process process;

	@Override
	public void handleMessage(ACLMessage message)
	{
		switch(message.getPerformative())
		{
			case PREDICT:
				handleRequest(message, 10);
			break;
			
			case INFORM:
				handleSearchResult(message);
			break;
			
			case RESUME:
				sendResults(message);
			break;
			
			default:
				LoggerUtil.log("Only supports PREDICT.");

		}
	}

	private void sendResults(ACLMessage message) 
	{
		try{
			process = Runtime.getRuntime().exec("python main.py data.csv " + '"' + message.getContent() + '"');
	        process.waitFor();
	        
	       	BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
	       	BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
	       	String line;

	       	while((line = outputReader.readLine()) != null){
	       		LoggerUtil.log(line);
	 	    }
	  	    
	       	while((line = errorReader.readLine()) != null){
	       		LoggerUtil.log(line);
	 	    }
	       	
	       	outputReader.close();
	       	errorReader.close();
	       	process.destroy();
	    }
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	private void handleSearchResult(ACLMessage message) 
	{
		ArrayList<Match> result = new ArrayList<Match>();

		ArrayList<Object> inputList = (ArrayList<Object>) message.getContentObj();
		if(inputList.size() == 0 )
			return;
		
		if(inputList.get(0) instanceof Match)
		{
			result = (ArrayList<Match>) message.getContentObj();
		}
		else
		{
			@SuppressWarnings("rawtypes")
			ArrayList<LinkedHashMap> temp = (ArrayList<LinkedHashMap>) message.getContentObj();

			for (@SuppressWarnings("rawtypes") LinkedHashMap lhm : temp)
			{
				Match m = new Match((String)lhm.get("key"), (String)lhm.get("homeTeam"), (String) lhm.get("awayTeam"), (String) lhm.get("homeGoals"), (String) lhm.get("awayGoals"));
				result.add(m);
				System.out.println(m.toString());
			}
		}		
		
		list.addUnique(result);
		
		for (Match m : list.getList()) {
			System.out.println(m.toString());
		}
		
		LoggerUtil.log("List populated with data");

	}

	private void handleRequest(ACLMessage message, int sleepSeconds)
	{
		FileWriter writer = null;
		try {
			writer = new FileWriter("data.csv");
			writer.append("HomeTeam,AwayTeam,FTHG,FTAG");
			writer.append("\n");
			for (Match m : list.getList()) {
				System.out.println(m.getHomeTeam() + " " + m.getAwayTeam() + " " + m.getHomeGoals() + " " + m.getAwayGoals());
				writer.append(m.getHomeTeam());
				writer.append(",");
				writer.append(m.getAwayTeam());
				writer.append(",");
				writer.append(m.getHomeGoals());
				writer.append(",");
				writer.append(m.getAwayGoals());
				writer.append("\n");
			}
			
			writer.flush();
    		writer.close();
    		System.out.println("Training file created!");
    		LoggerUtil.log("Starting prediction process");
    		System.out.println("Input string: " + message.getContent());

			ACLMessage pause = new ACLMessage();
			pause.setReceivers(new AID[]{this.getAid()});
			pause.setConversationID(message.getConversationID());
			pause.setSender(this.getAid());
			pause.setContent(message.getContent());
			pause.setPerformative(Performative.RESUME);	
			Thread t = new Thread()
			{
		        @Override
		        public void run() 
		        {
		        	 try 
		        	 {
						Thread.sleep(sleepSeconds*1000);
						new JMSQueue(pause);
		        	 }
		        	 catch (InterruptedException e)
		        	 {
						e.printStackTrace();
		        	 }
		        }
			};
			t.start();	
	        
		} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
	    }
	}
	
	public MatchesDTO getList() {
		return list;
	}

	public void setList(MatchesDTO list) {
		this.list = list;
	}
}
