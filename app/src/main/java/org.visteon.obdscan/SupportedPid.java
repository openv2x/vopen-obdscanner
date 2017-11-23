package org.visteon.obdscan;

/**
 * Created by mrangelo on 10/31/2017.
 */

public class SupportedPid {
    String pid = null;
    String name = null;
    boolean active = false;

    public SupportedPid(String pid, String name, boolean active) {
        super();
        this.pid = pid;
        this.name = name;
        this.active = active;
    }

    public String getPid() {
        return pid;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

}
