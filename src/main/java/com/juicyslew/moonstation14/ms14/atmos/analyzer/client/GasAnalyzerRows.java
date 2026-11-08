package com.juicyslew.moonstation14.ms14.atmos.analyzer.client;

import com.juicyslew.moonstation14.ms14.atmos.analyzer.*;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Single source of truth for displayed and scroll-counted analyzer rows. */
public final class GasAnalyzerRows {
    public enum Kind { HEADER, METRIC, GAS }
    public record Row(Kind kind, Component label, GasAnalyzerSample sample, GasAnalyzerSample.GasAmount gas) { }
    private GasAnalyzerRows() { }
    public static List<Row> project(GasAnalyzerSnapshot snapshot) {
        if(snapshot==null)return List.of();
        var rows=new ArrayList<Row>();
        if(snapshot.source()==null){
            if(snapshot.target()!=null) for(var gas:snapshot.target().gases())addGas(rows,snapshot.target(),gas);
            return List.copyOf(rows);
        }
        for(var entry:snapshot.nodeEntries()){
            Component heading=Component.translatable(entry.labelKey());
            rows.add(new Row(Kind.HEADER,heading,entry.sample(),null));
            rows.add(new Row(Kind.METRIC,Component.literal("P/T/V"),entry.sample(),null));
            for(var gas:entry.sample().gases())addGas(rows,entry.sample(),gas);
        }
        return List.copyOf(rows);
    }
    private static void addGas(List<Row> rows,GasAnalyzerSample sample,GasAnalyzerSample.GasAmount gas){
        rows.add(new Row(Kind.GAS,null,sample,gas));
    }
}
