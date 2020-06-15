package instantiableAgents;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;

import javax.ejb.Stateful;

import agent.AID;
import agent.Agent;
import jms.JMSQueue;
import locatorUtils.Match;
import message.ACLMessage;
import message.Performative;
import webSocket.LoggerUtil;

@SuppressWarnings("serial")
@Stateful
public class Locator extends Agent implements Serializable
{
	

	@Override
	public void handleMessage(ACLMessage message)
	{
		switch(message.getPerformative())
		{
			case SEARCH:
				
				if(!message.getSender().getType().getName().equals("Predictor"))
				{
					LoggerUtil.log("ERROR: Only Predictor agent is authorized to order a local search.");
					return;
				}
				
				handleSearch(message);
				
			break;
			
			default:
				LoggerUtil.log("SEARCH is the only performative this agent supports.");

		}
	}

	private void handleSearch(ACLMessage message) 
	{
		ArrayList<Match> records = new ArrayList<>();
		
		String path = "data.csv";
        String line = "";
        
        InputStream s = this.getClass().getResourceAsStream(path);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(s));	
		 try {
			 	// consume first line containing column name
			 	bufferedReader.readLine();
	            while ((line = bufferedReader.readLine()) != null) {

	                // use comma as separator
	                String[] values = line.split(",");
	                records.add(new Match(values[1]+values[2]+values[3], values[2], values[3], values[4], values[5]));
	            }
	                
			ACLMessage response = new ACLMessage();
			response.setPerformative(Performative.INFORM);
			response.setSender(this.getAid());
			response.setReceivers(new AID[] {message.getSender()});
			response.setContentObj(records);
			response.setContent(message.getContent());
			response.setConversationID(message.getConversationID());
			
			new JMSQueue(response);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			LoggerUtil.log("ERROR: No file found.");
		}
	}
	
}
