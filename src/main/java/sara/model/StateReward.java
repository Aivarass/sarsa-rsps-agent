package sara.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StateReward {
    double reward;
    State state;

    public StateReward(double reward, State state) {
        this.reward = reward;
        this.state = state;
    }

    public double getReward() { return reward; }
    public State getState() { return state; }
}
