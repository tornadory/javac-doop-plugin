/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package reporters;

import doop.CallGraphEdge;
import doop.InstanceFieldPointsTo;
import doop.VarPointsTo;

/**
 * @author anantoni
 */
public class ConsoleReporter implements Reporter {

    @Override
    public void reportVarPointsTo(VarPointsTo varPointsTo) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void reportCallGraphEdge(CallGraphEdge callGraphEdge) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void reportInstanceFieldPointsTo(InstanceFieldPointsTo instanceFieldPointsTo) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}