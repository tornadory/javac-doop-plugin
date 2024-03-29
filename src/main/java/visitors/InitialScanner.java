package visitors;

import com.sun.source.tree.LineMap;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;
import doop.DoopRepresentationBuilder;
import doop.HeapAllocation;
import doop.MethodDeclaration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import util.Position;

import static com.sun.tools.javac.code.Symbol.*;
import static com.sun.tools.javac.tree.JCTree.*;

/**
 * Created by anantoni on 11/6/2015.
 */

/**
 * A subclass of Tree.Visitor, this class defines
 * a general tree scanner pattern. Translation proceeds recursively in
 * left-to-right order down a tree. There is one visitor method in this class
 * for every possible kind of tree node.  To obtain a specific
 * scanner, it suffices to override those visitor methods which
 * do some interesting work. The scanner class itself takes care of all
 * navigational aspects.
 * <p>
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */

/**
 * InitialScanner scans the compilation unit for heap allocations and method definitions.
 */
public class InitialScanner extends TreeScanner {

    private final LineMap lineMap;
    private final DoopRepresentationBuilder doopReprBuilder;

    private ClassSymbol currentClassSymbol;
    private final Map<ClassSymbol, Map<String, Integer>> methodNamesPerClassMap;
    private MethodSymbol currentMethodSymbol;
    private String currentMethodDoopSignature;
    private String currentMethodCompactName;

    private Map<String, Set<Position>> fieldSignatureMap;

    private int heapAllocationCounter;
    private Map<String, Integer> heapAllocationCounterMap = null;

    /**
     * The following two maps will be used by the IdentifierScanner.
     */
    private Map<String, HeapAllocation> heapAllocationMap = null;
    private Map<String, MethodDeclaration> methodDeclarationMap = null;


    /**
     * *************************************************************************
     * Constructors
     * *************************************************************************
     */
    public InitialScanner() {
        this(null);
    }


    /**
     * @param lineMap holds the line, column information for each symbol.
     */
    public InitialScanner(LineMap lineMap) {
        this.doopReprBuilder = DoopRepresentationBuilder.getInstance();
        this.lineMap = lineMap;
        this.heapAllocationCounter = 0;
        this.methodNamesPerClassMap = new HashMap<>();
        this.heapAllocationCounterMap = new HashMap<>();
        this.heapAllocationMap = new HashMap<>();
        this.methodDeclarationMap = new HashMap<>();
        this.fieldSignatureMap = new HashMap<>();
    }


    /**
     * *************************************************************************
     * Getters and Setters
     * *************************************************************************
     */
    public Map<String, HeapAllocation> getHeapAllocationMap() {
        return heapAllocationMap;
    }

    public void setHeapAllocationMap(Map<String, HeapAllocation> heapAllocationMap) {
        this.heapAllocationMap = heapAllocationMap;
    }

    public Map<String, MethodDeclaration> getMethodDeclarationMap() {
        return methodDeclarationMap;
    }

    public void setMethodDeclarationMap(Map<String, MethodDeclaration> methodDeclarationMap) {
        this.methodDeclarationMap = methodDeclarationMap;
    }

    public Map<String, Set<Position>> getFieldSignatureMap() {
        return fieldSignatureMap;
    }

    public void setFieldSignatureMap(Map<String, Set<Position>> fieldSignatureMap) {
        this.fieldSignatureMap = fieldSignatureMap;
    }

    /**
     * Visitor method: Scan a single node.
     *
     * @param tree
     */
    @Override
    public void scan(JCTree tree) {
        if (tree != null) tree.accept(this);
    }

    /**
     * Visitor method: scan a list of nodes.
     *
     * @param trees
     */
    @Override
    public void scan(List<? extends JCTree> trees) {
        if (trees != null)
            for (List<? extends JCTree> l = trees; l.nonEmpty(); l = l.tail)
                scan(l.head);
    }


    /**
     * *************************************************************************
     * Visitor methods
     * **************************************************************************
     */
    /**
     *
     * @param tree
     */
    @Override
    public void visitTopLevel(JCCompilationUnit tree) {
        scan(tree.packageAnnotations);
        scan(tree.pid);
        scan(tree.defs);
    }

    /**
     *
     * @param tree
     */
    @Override
    public void visitImport(JCImport tree) {
        scan(tree.qualid);
    }

    /**
     * Visit class declaration AST node.
     *
     * @param tree
     */
    @Override
    public void visitClassDef(JCClassDecl tree) {

        this.currentClassSymbol = tree.sym;
        Map<String, Integer> methodNamesMap;
        if (!methodNamesPerClassMap.containsKey(this.currentClassSymbol)) {
            methodNamesMap = new HashMap<>();
            /**
             * Fills the method names map in order to be able to identify overloaded methods for each class.
             */
            for (Symbol symbol : this.currentClassSymbol.getEnclosedElements()) {
                if (symbol instanceof MethodSymbol) {
                    MethodSymbol methodSymbol = (MethodSymbol)symbol;
                    if (!methodNamesMap.containsKey(methodSymbol.getQualifiedName().toString()))
                        methodNamesMap.put(methodSymbol.getQualifiedName().toString(), 1);
                    else {
                        int methodNameCounter = methodNamesMap.get(methodSymbol.getQualifiedName().toString());
                        methodNamesMap.put(methodSymbol.getQualifiedName().toString(), ++methodNameCounter);
                    }
                }
            }
            methodNamesPerClassMap.put(this.currentClassSymbol, methodNamesMap);
        }

        scan(tree.mods);
        scan(tree.typarams);
        scan(tree.extending);
        scan(tree.implementing);
        scan(tree.defs);
    }

    /**
     * Visit method declaration AST node.
     *
     * @param tree
     */
    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        this.currentMethodSymbol = tree.sym;
        this.currentMethodDoopSignature = this.doopReprBuilder.buildDoopMethodSignature(currentMethodSymbol);
        this.currentMethodCompactName = this.doopReprBuilder.buildDoopMethodCompactName(currentMethodSymbol);

        scan(tree.mods);
        scan(tree.restype);
        methodDeclarationMap.put(this.currentMethodDoopSignature,
                                    new MethodDeclaration(lineMap.getLineNumber(tree.pos),
                                                            lineMap.getColumnNumber(tree.pos),
                                                            lineMap.getColumnNumber(tree.pos + tree.name.toString().length()),
                                                            this.currentMethodDoopSignature));

        scan(tree.typarams);
        scan(tree.recvparam);
        scan(tree.params);
        scan(tree.thrown);
        scan(tree.defaultValue);
        scan(tree.body);
    }

    /**
     * Visit variable declaration AST node.
     *
     * @param tree
     */
    @Override
    public void visitVarDef(JCVariableDecl tree) {
        scan(tree.mods);
        scan(tree.vartype);
        scan(tree.nameexpr);
        scan(tree.init);
    }

    @Override
    public void visitSkip(JCSkip tree) {
    }

    /**
     * @param tree
     */
    @Override
    public void visitBlock(JCBlock tree) {
        scan(tree.stats);
    }

    /**
     *
     * @param tree
     */
    @Override
    public void visitDoLoop(JCDoWhileLoop tree) {
        scan(tree.body);
        scan(tree.cond);
    }

    /**
     *
     * @param tree
     */
    @Override
    public void visitWhileLoop(JCWhileLoop tree) {
        scan(tree.cond);
        scan(tree.body);
    }

    /**
     *
     * @param tree
     */
    @Override
    public void visitForLoop(JCForLoop tree) {
        scan(tree.init);
        scan(tree.cond);
        scan(tree.step);
        scan(tree.body);
    }

    /**
     *
     * @param tree
     */
    @Override
    public void visitForeachLoop(JCEnhancedForLoop tree) {
        scan(tree.var);
        scan(tree.expr);
        scan(tree.body);
    }

    @Override
    public void visitLabelled(JCLabeledStatement tree) {
        scan(tree.body);
    }

    @Override
    public void visitSwitch(JCSwitch tree) {
        scan(tree.selector);
        scan(tree.cases);
    }

    @Override
    public void visitCase(JCCase tree) {
        scan(tree.pat);
        scan(tree.stats);
    }

    @Override
    public void visitSynchronized(JCSynchronized tree) {
        scan(tree.lock);
        scan(tree.body);
    }

    @Override
    public void visitTry(JCTry tree) {
        scan(tree.resources);
        scan(tree.body);
        scan(tree.catchers);
        scan(tree.finalizer);
    }

    @Override
    public void visitCatch(JCCatch tree) {
        scan(tree.param);
        scan(tree.body);
    }

    @Override
    public void visitConditional(JCConditional tree) {
        scan(tree.cond);
        scan(tree.truepart);
        scan(tree.falsepart);
    }

    @Override
    public void visitIf(JCIf tree) {
        scan(tree.cond);
        scan(tree.thenpart);
        scan(tree.elsepart);
    }

    @Override
    public void visitExec(JCExpressionStatement tree) {
        scan(tree.expr);
    }

    @Override
    public void visitBreak(JCBreak tree) {
    }

    @Override
    public void visitContinue(JCContinue tree) {
    }

    @Override
    public void visitReturn(JCReturn tree) {
        scan(tree.expr);
    }

    @Override
    public void visitThrow(JCThrow tree) {
        scan(tree.expr);
    }

    @Override
    public void visitAssert(JCAssert tree) {
        scan(tree.cond);
        scan(tree.detail);
    }

    @Override
    public void visitApply(JCMethodInvocation tree) {
        scan(tree.typeargs);
        scan(tree.meth);
        scan(tree.args);
    }

    /**
     * Visit "new Klass()" AST node.
     *
     * @param tree
     */
    @Override
    public void visitNewClass(JCNewClass tree) {
        scan(tree.encl);
        scan(tree.typeargs);
        scan(tree.clazz);
        scan(tree.args);
        scan(tree.def);

        String heapAllocation;
        /**
         * If current method is overloaded use its signature to build the heap allocation.
         */
        if (this.methodNamesPerClassMap.get(this.currentClassSymbol).get(this.currentMethodSymbol.getQualifiedName().toString()) > 1)
            heapAllocation = this.doopReprBuilder.buildDoopHeapAllocation(currentMethodDoopSignature, tree.clazz.type.getOriginalType().toString());
        /**
         * Otherwise use its compact name.
         */
        else
            heapAllocation = this.doopReprBuilder.buildDoopHeapAllocation(currentMethodCompactName, tree.clazz.type.getOriginalType().toString());

        /**
         * Evaluate heap allocation counter within method.
         */
        if (heapAllocationCounterMap.containsKey(heapAllocation)) {
            heapAllocationCounter = heapAllocationCounterMap.get(heapAllocation) + 1;
            heapAllocationCounterMap.put(heapAllocation, heapAllocationCounter);
        }
        else {
            heapAllocationCounter = 0;
            heapAllocationCounterMap.put(heapAllocation, 0);
        }
        heapAllocation += "/" + heapAllocationCounter;

        /**
         * Report Heap Allocation
         */
        heapAllocationMap.put(heapAllocation, new HeapAllocation(lineMap.getLineNumber(tree.clazz.pos),
                                                                    lineMap.getColumnNumber(tree.clazz.pos),
                                                                    lineMap.getColumnNumber(tree.clazz.pos + tree.clazz.toString().length()),
                                                                    heapAllocation));
        System.out.println("Found HeapAllocation: " + heapAllocation);
    }

    @Override
    public void visitNewArray(JCNewArray tree) {
        scan(tree.annotations);
        scan(tree.elemtype);
        scan(tree.dims);
        tree.dimAnnotations.stream().forEach(this::scan);
        scan(tree.elems);
    }

    @Override
    public void visitLambda(JCLambda tree) {
        scan(tree.body);
        scan(tree.params);
    }

    @Override
    public void visitParens(JCParens tree) {
        scan(tree.expr);
    }

    @Override
    public void visitAssign(JCAssign tree) {
        scan(tree.lhs);
        scan(tree.rhs);
    }

    @Override
    public void visitAssignop(JCAssignOp tree) {
        scan(tree.lhs);
        scan(tree.rhs);
    }

    @Override
    public void visitUnary(JCUnary tree) {
        scan(tree.arg);
    }

    @Override
    public void visitBinary(JCBinary tree) {
        scan(tree.lhs);
        scan(tree.rhs);
    }

    @Override
    public void visitTypeCast(JCTypeCast tree) {
        scan(tree.clazz);
        scan(tree.expr);
    }

    @Override
    public void visitTypeTest(JCInstanceOf tree) {
        scan(tree.expr);
        scan(tree.clazz);
    }

    @Override
    public void visitIndexed(JCArrayAccess tree) {
        scan(tree.indexed);
        scan(tree.index);
    }

    /**
     * Visit field access AST node.
     *
     * @param tree
     */
    @Override
    public void visitSelect(JCFieldAccess tree) {
        scan(tree.selected);
        if (tree.sym != null && tree.sym instanceof VarSymbol) {
            System.out.println(tree.sym.getClass());
            String fieldSignature = this.doopReprBuilder.buildDoopFieldSignature((VarSymbol) tree.sym);
            System.out.println("Field Signature: " + fieldSignature);

            if (this.fieldSignatureMap.containsKey(fieldSignature)) {
                if (this.lineMap.getLineNumber(((VarSymbol) tree.sym).pos) > 0)
                    this.fieldSignatureMap.get(fieldSignature).add(new Position(this.lineMap.getLineNumber(tree.pos),
                            this.lineMap.getColumnNumber(tree.pos),
                            this.lineMap.getColumnNumber(tree.pos + tree.sym.getQualifiedName().toString().length())));
            }
            else {
                Set<Position> positionSet = new HashSet<>();
                if (this.lineMap.getLineNumber(((VarSymbol) tree.sym).pos) > 0)
                    positionSet.add(new Position(this.lineMap.getLineNumber(tree.pos),
                            this.lineMap.getColumnNumber(tree.pos),
                            this.lineMap.getColumnNumber(tree.pos + tree.sym.getQualifiedName().toString().length())));

                this.fieldSignatureMap.put(fieldSignature, positionSet);
            }
            System.out.println(this.fieldSignatureMap);
        }
    }

    @Override
    public void visitReference(JCMemberReference tree) {
        scan(tree.expr);
        scan(tree.typeargs);
    }

    @Override
    public void visitIdent(JCIdent tree) {
    }

    @Override
    public void visitLiteral(JCLiteral tree) {
    }

    @Override
    public void visitTypeIdent(JCPrimitiveTypeTree tree) {
    }

    @Override
    public void visitTypeArray(JCArrayTypeTree tree) {
        scan(tree.elemtype);
    }

    @Override
    public void visitTypeApply(JCTypeApply tree) {
        scan(tree.clazz);
        scan(tree.arguments);
    }

    @Override
    public void visitTypeUnion(JCTypeUnion tree) {
        scan(tree.alternatives);
    }

    @Override
    public void visitTypeIntersection(JCTypeIntersection tree) {
        scan(tree.bounds);
    }

    @Override
    public void visitTypeParameter(JCTypeParameter tree) {
        scan(tree.annotations);
        scan(tree.bounds);
    }

    @Override
    public void visitWildcard(JCWildcard tree) {
        scan(tree.kind);
        if (tree.inner != null)
            scan(tree.inner);
    }

    @Override
    public void visitTypeBoundKind(TypeBoundKind that) {
    }

    @Override
    public void visitModifiers(JCModifiers tree) {
        scan(tree.annotations);
    }

    @Override
    public void visitAnnotation(JCAnnotation tree) {
        scan(tree.annotationType);
        scan(tree.args);
    }

    @Override
    public void visitAnnotatedType(JCAnnotatedType tree) {
        scan(tree.annotations);
        scan(tree.underlyingType);
    }

    @Override
    public void visitErroneous(JCErroneous tree) {
    }

    @Override
    public void visitLetExpr(LetExpr tree) {
        scan(tree.defs);
        scan(tree.expr);
    }

    @Override
    public void visitTree(JCTree tree) {
        Assert.error();
    }
}