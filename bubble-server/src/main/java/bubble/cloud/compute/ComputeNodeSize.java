/**
 * Copyright (c) 2020 Bubble, Inc.  All rights reserved.
 * For personal (non-commercial) use, see license: https://getbubblenow.com/bubble-license/
 */
package bubble.cloud.compute;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(chain=true)
public class ComputeNodeSize {

    @Getter @Setter private ComputeNodeSizeType type;
    @Getter @Setter private String name;
    @Setter private String internalName;
    public String getInternalName () { return internalName != null ? internalName : name; }

    @Getter @Setter private String description;
    @Getter @Setter private int vcpu;
    @Getter @Setter private int memoryMB;
    @Getter @Setter private int ssdGB;
    @Getter @Setter private int hddGB;
    @Getter @Setter private Integer networkMbps;

    @Setter private Integer costUnits;
    public Integer getCostUnits () { return costUnits != null ? costUnits : type != null ? type.getCostUnits() : null; }

}
