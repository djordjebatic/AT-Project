import numpy as np
import pandas as pd
import os
from scipy.stats import poisson
import statsmodels.api as sm
import statsmodels.formula.api as smf

aggregate = []

for root, dirs, files in os.walk("Dataset", topdown=False):
    for name in files:
        file_name = os.path.join(root, name)
        df = pd.read_csv(file_name, index_col=None, header=0)
        aggregate.append(df)

df = pd.concat(aggregate, axis=0, ignore_index=True, sort=True)

match_data = df[['HomeTeam', 'AwayTeam', 'FTHG', 'FTAG']]
match_data['HGD'] = match_data['FTHG'] - match_data['FTAG']
match_data['AGD'] = match_data['FTAG'] - match_data['FTHG']

goal_model_data = pd.concat([match_data[['HomeTeam', 'AwayTeam', 'HGD']].assign(home=1).rename(
    columns={'HomeTeam': 'team', 'AwayTeam': 'opponent', 'HGD': 'goals'}),
    match_data[['AwayTeam', 'HomeTeam', 'AGD']].assign(home=0).rename(
        columns={'AwayTeam': 'team', 'HomeTeam': 'opponent', 'AGD': 'goals'})])

poisson_model = smf.glm(formula="goals ~ home + team + opponent", data=goal_model_data,
                        family=sm.families.Poisson()).fit()

print(poisson_model.summary())


def simulate_match(foot_model, home_team, away_team, max_goals=10):
    home_goals_avg = foot_model.predict(pd.DataFrame(data={'team': home_team,
                                                           'opponent': away_team, 'home': 1},
                                                     index=[1])).values[0]
    away_goals_avg = foot_model.predict(pd.DataFrame(data={'team': away_team,
                                                           'opponent': home_team, 'home': 0},
                                                     index=[1])).values[0]
    team_pred = [[poisson.pmf(i, team_avg) for i in range(0, max_goals + 1)] for team_avg in
                 [home_goals_avg, away_goals_avg]]

    return np.outer(np.array(team_pred[0]), np.array(team_pred[1]))

def calculate_ods(home, away, max_goals=10):
    res = simulate_match(poisson_model, home, away, max_goals)
    print('Chance of home team (' + home + ') winning: {:.4f}'.format(np.sum(np.tril(res, -1))))
    print('Chance of draw: {:.4f}'.format(np.sum(np.diag(res))))
    print('Chance of away team (' + away + ') winning: {:.4f}'.format(np.sum(np.triu(res, 1))))


if __name__ == '__main__':
    calculate_ods('Aston Villa', 'Watford')
