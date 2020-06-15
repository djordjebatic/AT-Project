package webSocket.dto;

import java.io.Serializable;
import java.util.ArrayList;

import locatorUtils.Match;

@SuppressWarnings("serial")
public class MatchesDTO implements Serializable
{
	ArrayList<Match> list = new ArrayList<Match>();

	
	public MatchesDTO()
	{
		list = new ArrayList<Match>();
	}
	
	public MatchesDTO(ArrayList<Match> list)
	{
		super();
		this.list = list;
	}

	public ArrayList<Match> getList() 
	{
		return list;
	}

	public void setList(ArrayList<Match> list)
	{
		this.list = list;
	}
	
	public void addUnique(ArrayList<Match> newList)
	{
		boolean found;
		for(Match m1 : newList)
		{
			found = false;
			for(Match m2 : list)
			{
				if(m1.getKey().equals(m2.getKey()))
				{
					found = true;
				}
			}	
			if(!found)
				list.add(m1);
		}
	}

	public void empty() 
	{
		list.clear();
	}
}

