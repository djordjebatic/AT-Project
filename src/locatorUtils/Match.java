package locatorUtils;

import java.io.Serializable;

@SuppressWarnings("serial")
public class Match implements Serializable{
	
	private String key;
	private String homeTeam;
	private String awayTeam;
	private String homeGoals;
	private String awayGoals;
	
	public Match(String key, String homeTeam, String awayTeam, String homeGoals, String awayGoals) {
		super();
		this.key = key;
		this.homeTeam = homeTeam;
		this.awayTeam = awayTeam;
		this.homeGoals = homeGoals;
		this.awayGoals = awayGoals;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getHomeTeam() {
		return homeTeam;
	}

	public void setHomeTeam(String homeTeam) {
		this.homeTeam = homeTeam;
	}

	public String getAwayTeam() {
		return awayTeam;
	}

	public void setAwayTeam(String awayTeam) {
		this.awayTeam = awayTeam;
	}

	public String getHomeGoals() {
		return homeGoals;
	}

	public void setHomeGoals(String homeGoals) {
		this.homeGoals = homeGoals;
	}

	public String getAwayGoals() {
		return awayGoals;
	}

	public void setAwayGoals(String awayGoals) {
		this.awayGoals = awayGoals;
	}

	@Override
	public String toString() {
		return "Match [key=" + key + ", homeTeam=" + homeTeam + ", awayTeam=" + awayTeam + ", homeGoals=" + homeGoals
				+ ", awayGoals=" + awayGoals + "]";
	}
}
