package sara.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GroundItemsDto {
    private int itemOnGround;

    public int getItemOnGround() { return itemOnGround; }
    public void setItemOnGround(int itemOnGround) { this.itemOnGround = itemOnGround; }
}
