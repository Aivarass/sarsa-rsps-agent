package sara.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InventoryDto {
    private int foodCountRemaining;
    private int healAmountRemaining;

    public int getFoodCountRemaining() { return foodCountRemaining; }
    public void setFoodCountRemaining(int foodCountRemaining) { this.foodCountRemaining = foodCountRemaining; }
    public int getHealAmountRemaining() { return healAmountRemaining; }
    public void setHealAmountRemaining(int healAmountRemaining) { this.healAmountRemaining = healAmountRemaining; }
}
