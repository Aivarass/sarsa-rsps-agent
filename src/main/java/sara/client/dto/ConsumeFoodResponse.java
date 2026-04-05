package sara.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsumeFoodResponse {
    private String item;
    private int healed;

    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }
    public int getHealed() { return healed; }
    public void setHealed(int healed) { this.healed = healed; }
}
