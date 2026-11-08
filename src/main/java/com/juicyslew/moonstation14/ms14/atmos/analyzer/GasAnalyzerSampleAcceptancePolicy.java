package com.juicyslew.moonstation14.ms14.atmos.analyzer;

/** Shared correlation gate: a remote graph is current only for its accepted range and derived cell. */
public final class GasAnalyzerSampleAcceptancePolicy {
    private GasAnalyzerSampleAcceptancePolicy() { }
    public static boolean responseMatchesTarget(GasAnalyzerSnapshot state,int desiredDistance,long expectedControl,
                                                GasAnalyzerCellSelection.Result predicted) {
        if(state==null||predicted==null||state.closed()||state.requestedDistance()!=desiredDistance
                ||expectedControl>0&&state.controlSequence()<expectedControl)return false;
        // Pinned node scans are server-admitted world targets, not client ray projections.
        // Their identity is deliberately not reconstructed from client coordinates.
        if(state.source()!=null)return state.status()==GasAnalyzerSnapshot.Status.READY&&state.targetCell()!=null;
        if(predicted.status()==GasAnalyzerCellSelection.Status.UNLOADED)
            return state.targetCell()==null&&state.status()==GasAnalyzerSnapshot.Status.UNAVAILABLE;
        if(predicted.status()==GasAnalyzerCellSelection.Status.BLOCKED&&predicted.cell()==null)
            return state.targetCell()==null&&state.status()==GasAnalyzerSnapshot.Status.BLOCKED;
        if(state.targetCell()==null||!state.targetCell().equals(predicted.cell()))return false;
        return predicted.status()==GasAnalyzerCellSelection.Status.BLOCKED
                ? state.status()==GasAnalyzerSnapshot.Status.BLOCKED
                : state.status()!=GasAnalyzerSnapshot.Status.BLOCKED;
    }
}
