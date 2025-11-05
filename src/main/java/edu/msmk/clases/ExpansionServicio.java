package edu.msmk.clases;

import java.util.HashSet;
import java.util.Set;

public class ExpansionServicio {
    private Set<String> pueblos;

    public ExpansionServicio() {
        this.pueblos = new HashSet<>();
    }

    public void addPueblo(String pueblo) {
        this.pueblos.add(pueblo);
    }

    public int numeroPueblos() {
        return pueblos.size();
    }

    public boolean DamoServicio(String pueblo){
        return pueblos.contains(pueblo);

    }
}
