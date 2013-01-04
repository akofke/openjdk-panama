/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.nashorn.internal.codegen;

import static jdk.nashorn.internal.codegen.ClassEmitter.Flag.PRIVATE;
import static jdk.nashorn.internal.codegen.ClassEmitter.Flag.STATIC;
import static jdk.nashorn.internal.codegen.CompilerConstants.GET_MAP;
import static jdk.nashorn.internal.codegen.CompilerConstants.GET_STRING;
import static jdk.nashorn.internal.codegen.CompilerConstants.LEAF;
import static jdk.nashorn.internal.codegen.CompilerConstants.QUICK_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.REGEX_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.SCOPE;
import static jdk.nashorn.internal.codegen.CompilerConstants.SPLIT_ARRAY_ARG;
import static jdk.nashorn.internal.codegen.CompilerConstants.SPLIT_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.interfaceCallNoLookup;
import static jdk.nashorn.internal.codegen.CompilerConstants.methodDescriptor;
import static jdk.nashorn.internal.codegen.CompilerConstants.staticCallNoLookup;
import static jdk.nashorn.internal.codegen.CompilerConstants.staticField;
import static jdk.nashorn.internal.codegen.CompilerConstants.typeDescriptor;
import static jdk.nashorn.internal.ir.Symbol.IS_INTERNAL;
import static jdk.nashorn.internal.ir.Symbol.IS_TEMP;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_FAST_SCOPE;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_FUNCTION_DECLARATION;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_SCOPE;
import static jdk.nashorn.internal.runtime.linker.NashornCallSiteDescriptor.CALLSITE_STRICT;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import jdk.nashorn.internal.codegen.ClassEmitter.Flag;
import jdk.nashorn.internal.codegen.CompilerConstants.Call;
import jdk.nashorn.internal.codegen.MethodEmitter.Condition;
import jdk.nashorn.internal.codegen.MethodEmitter.Label;
import jdk.nashorn.internal.codegen.RuntimeCallSite.SpecializedRuntimeNode;
import jdk.nashorn.internal.codegen.objects.FieldObjectCreator;
import jdk.nashorn.internal.codegen.objects.FunctionObjectCreator;
import jdk.nashorn.internal.codegen.objects.MapCreator;
import jdk.nashorn.internal.codegen.objects.ObjectMapCreator;
import jdk.nashorn.internal.codegen.types.ArrayType;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BaseNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.BreakNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.ContinueNode;
import jdk.nashorn.internal.ir.DoWhileNode;
import jdk.nashorn.internal.ir.EmptyNode;
import jdk.nashorn.internal.ir.ExecuteNode;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.LineNumberNode;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode.ArrayUnit;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ObjectNode;
import jdk.nashorn.internal.ir.PropertyNode;
import jdk.nashorn.internal.ir.ReferenceNode;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.RuntimeNode.Request;
import jdk.nashorn.internal.ir.SplitNode;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.TernaryNode;
import jdk.nashorn.internal.ir.ThrowNode;
import jdk.nashorn.internal.ir.TryNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.WhileNode;
import jdk.nashorn.internal.ir.WithNode;
import jdk.nashorn.internal.ir.debug.ASTWriter;
import jdk.nashorn.internal.ir.visitor.NodeOperatorVisitor;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Lexer.RegexToken;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ECMAException;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.Scope;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.Undefined;
import jdk.nashorn.internal.runtime.linker.LinkerCallSite;

/**
 * This is the lowest tier of the code generator. It takes lowered ASTs emitted
 * from Lower and emits Java byte code. The byte code emission logic is broken
 * out into MethodEmitter. MethodEmitter works internally with a type stack, and
 * keeps track of the contents of the byte code stack. This way we avoid a large
 * number of special cases on the form
 * <pre>
 * {@code
 * if (type == INT) {
 *     visitInsn(ILOAD, slot);
 * } else if (type == DOUBLE) {
 *     visitInsn(DOUBLE, slot);
 * }
 * }
 * </pre>
 * This quickly became apparent when the code generator was generalized to work
 * with all types, and not just numbers or objects.
 * <p>
 * The CodeGenerator visits nodes only once, tags them as resolved and emits
 * bytecode for them.
 */
public final class CodeGenerator extends NodeOperatorVisitor {

    /** Current compiler */
    private final Compiler compiler;

    /** Compiler context */
    private final Context context;

    /** Call site flags given to the code generator to be used for all generated call sites */
    private final int callSiteFlags;

    /** How many regexp fields have been emitted */
    private int regexFieldCount;

    /** Map of shared scope call sites */
    private final Map<SharedScopeCall, SharedScopeCall> scopeCalls = new HashMap<>();

    /** When should we stop caching regexp expressions in fields to limit bytecode size? */
    private static final int MAX_REGEX_FIELDS = 2 * 1024;

    /**
     * Constructor.
     *
     * @param compiler
     */
    CodeGenerator(final Compiler compiler) {
        this.compiler      = compiler;
        this.context       = compiler.getContext();
        this.callSiteFlags = context._callsite_flags;
    }

    /**
     * Get the compiler
     *
     * @return the compiler used
     */
    public Compiler getCompiler() {
        return compiler;
    }

    /**
     * Gets the call site flags, adding the strict flag if the current function
     * being generated is in strict mode
     *
     * @return the correct flags for a call site in the current function
     */
    public int getCallSiteFlags() {
        return getCurrentFunctionNode().isStrictMode() ? callSiteFlags | CALLSITE_STRICT : callSiteFlags;
    }

    /**
     * Load an identity node
     *
     * @param identNode an identity node to load
     * @return the method generator used
     */
    private MethodEmitter loadIdent(final IdentNode identNode) {
        final Symbol symbol = identNode.getSymbol();

        if (!symbol.isScope()) {
            assert symbol.hasSlot() && symbol.getSlot() != 0 || symbol.isThis();
            return method.load(symbol);
        }

        final String name = symbol.getName();

        if (CompilerConstants.__FILE__.name().equals(name)) {
            return method.load(identNode.getSource().getName());
        } else if (CompilerConstants.__DIR__.name().equals(name)) {
            return method.load(identNode.getSource().getBase());
        } else if (CompilerConstants.__LINE__.name().equals(name)) {
            return method.load(identNode.getSource().getLine(identNode.position())).convert(Type.OBJECT);
        } else {
            assert identNode.getSymbol().isScope() : identNode + " is not in scope!";

            final int flags = CALLSITE_SCOPE | getCallSiteFlags();
            method.loadScope();

            if (symbol.isFastScope(getCurrentFunctionNode())) {
                // Only generate shared scope getter for fast-scope symbols so we know we can dial in correct scope.
                if (symbol.getUseCount() > SharedScopeCall.FAST_SCOPE_GET_THRESHOLD) {
                    return loadSharedScopeVar(identNode.getType(), symbol, flags);
                }
                return loadFastScopeVar(identNode.getType(), symbol, flags, identNode.isFunction());
            }
            return method.dynamicGet(identNode.getType(), identNode.getName(), flags, identNode.isFunction());
        }
    }

    private MethodEmitter loadSharedScopeVar(final Type valueType, final Symbol symbol, final int flags) {
        method.load(symbol.isFastScope(getCurrentFunctionNode()) ? getScopeProtoDepth(getCurrentBlock(), symbol) : -1);
        final SharedScopeCall scopeCall = getScopeGet(valueType, symbol, flags | CALLSITE_FAST_SCOPE);
        scopeCall.generateInvoke(method);
        return method;
    }

    private MethodEmitter loadFastScopeVar(final Type valueType, final Symbol symbol, final int flags, final boolean isMethod) {
        loadFastScopeProto(symbol, false);
        method.dynamicGet(valueType, symbol.getName(), flags | CALLSITE_FAST_SCOPE, isMethod);
        return method;
    }

    private MethodEmitter storeFastScopeVar(final Type valueType, final Symbol symbol, final int flags) {
        loadFastScopeProto(symbol, true);
        method.dynamicSet(valueType, symbol.getName(), flags | CALLSITE_FAST_SCOPE);
        return method;
    }

    private static int getScopeProtoDepth(final Block currentBlock, final Symbol symbol) {
        if (currentBlock == symbol.getBlock()) {
            return 0;
        }

        final int   delta       = currentBlock.needsScope() ? 1 : 0;
        final Block parentBlock = currentBlock.getParent();

        if (parentBlock != null) {
            final int result = getScopeProtoDepth(parentBlock, symbol);
            if (result != -1) {
                return delta + result;
            }
        }

        if (currentBlock instanceof FunctionNode) {
            for (final Block lookupBlock : ((FunctionNode)currentBlock).getReferencingParentBlocks()) {
                final int result = getScopeProtoDepth(lookupBlock, symbol);
                if (result != -1) {
                    return delta + result;
                }
            }
        }

        return -1;
    }

    private void loadFastScopeProto(final Symbol symbol, final boolean swap) {
        final int depth = getScopeProtoDepth(getCurrentBlock(), symbol);
        assert depth != -1;
        if(depth > 0) {
            if (swap) {
                method.swap();
            }
            for (int i = 0; i < depth; i++) {
                method.invoke(ScriptObject.GET_PROTO);
            }
            if (swap) {
                method.swap();
            }
        }
    }

    /**
     * Generate code that loads this node to the stack. This method is only
     * public to be accessible from the maps sub package. Do not call externally
     *
     * @param node node to load
     *
     * @return the method emitter used
     */
    public MethodEmitter load(final Node node) {
        return load(node, false);
    }

    private MethodEmitter load(final Node node, final boolean baseAlreadyOnStack) {
        final Symbol symbol = node.getSymbol();

        // If we lack symbols, we just generate what we see.
        if (symbol == null) {
            node.accept(this);
            return method;
        }

        /*
         * The load may be of type IdentNode, e.g. "x", AccessNode, e.g. "x.y"
         * or IndexNode e.g. "x[y]". Both AccessNodes and IndexNodes are
         * BaseNodes and the logic for loading the base object is reused
         */
        final CodeGenerator codegen = this;

        node.accept(new NodeVisitor(compileUnit, method) {
            @Override
            public Node enter(final IdentNode identNode) {
                loadIdent(identNode);
                return null;
            }

            @Override
            public Node enter(final AccessNode accessNode) {
                if (!baseAlreadyOnStack) {
                    load(accessNode.getBase()).convert(Type.OBJECT);
                }
                assert method.peekType().isObject();
                method.dynamicGet(node.getType(), accessNode.getProperty().getName(), getCallSiteFlags(), accessNode.isFunction());
                return null;
            }

            @Override
            public Node enter(final IndexNode indexNode) {
                if (!baseAlreadyOnStack) {
                    load(indexNode.getBase());
                    load(indexNode.getIndex());
                }
                method.dynamicGetIndex(node.getType(), getCallSiteFlags(), indexNode.isFunction());
                return null;
            }

            @Override
            public Node enterDefault(final Node otherNode) {
                otherNode.accept(codegen); // generate code for whatever we are looking at.
                method.load(symbol); // load the final symbol to the stack (or nop if no slot, then result is already there)
                return null;
            }
        });

        return method;
    }

    @Override
    public Node enter(final AccessNode accessNode) {
        if (accessNode.testResolved()) {
            return null;
        }

        load(accessNode);

        return null;
    }

    /**
     * Initialize a specific set of vars to undefined. This has to be done at
     * the start of each method for local variables that aren't passed as
     * parameters.
     *
     * @param symbols list of symbols.
     */
    private void initSymbols(final Iterable<Symbol> symbols) {
        final LinkedList<Symbol> numbers = new LinkedList<>();
        final LinkedList<Symbol> objects = new LinkedList<>();

        for (final Symbol symbol : symbols) {
            /*
             * The following symbols are guaranteed to be defined and thus safe
             * from having unsigned written to them: parameters internals this
             *
             * Otherwise we must, unless we perform control/escape analysis,
             * assign them undefined.
             */
            final boolean isInternal = symbol.isParam() || symbol.isInternal() || symbol.isThis() || !symbol.canBeUndefined();

            if (symbol.hasSlot() && !isInternal) {
                assert symbol.getSymbolType().isNumber() || symbol.getSymbolType().isObject() : "no potentially undefined narrower local vars than doubles are allowed: " + symbol + " in " + getCurrentFunctionNode();
                if (symbol.getSymbolType().isNumber()) {
                    numbers.add(symbol);
                } else if (symbol.getSymbolType().isObject()) {
                    objects.add(symbol);
                }
            }
        }

        initSymbols(numbers, Type.NUMBER);
        initSymbols(objects, Type.OBJECT);
    }

    private void initSymbols(final LinkedList<Symbol> symbols, final Type type) {
        if (symbols.isEmpty()) {
            return;
        }

        method.loadUndefined(type);
        while (!symbols.isEmpty()) {
            final Symbol symbol = symbols.removeFirst();
            if (!symbols.isEmpty()) {
                method.dup();
            }
            method.store(symbol);
        }
    }

    /**
     * Create symbol debug information.
     *
     * @param block block containing symbols.
     */
    private void symbolInfo(final Block block) {
        for (final Symbol symbol : block.getFrame().getSymbols()) {
            method.localVariable(symbol, block.getEntryLabel(), block.getBreakLabel());
        }
    }

    @Override
    public Node enter(final Block block) {
        if (block.testResolved()) {
            return null;
        }

        method.label(block.getEntryLabel());
        initLocals(block);

        return block;
    }

    @Override
    public Node leave(final Block block) {
        method.label(block.getBreakLabel());
        symbolInfo(block);

        if (block.needsScope()) {
            popBlockScope(block);
        }

        return block;
    }

    private void popBlockScope(final Block block) {
        final Label exitLabel     = new Label("block_exit");
        final Label recoveryLabel = new Label("block_catch");
        final Label skipLabel     = new Label("skip_catch");

        /* pop scope a la try-finally */
        method.loadScope();
        method.invoke(ScriptObject.GET_PROTO);
        method.storeScope();
        method._goto(skipLabel);
        method.label(exitLabel);

        method._catch(recoveryLabel);
        method.loadScope();
        method.invoke(ScriptObject.GET_PROTO);
        method.storeScope();
        method.athrow();
        method.label(skipLabel);
        method._try(block.getEntryLabel(), exitLabel, recoveryLabel, Throwable.class);
    }

    @Override
    public Node enter(final BreakNode breakNode) {
        if (breakNode.testResolved()) {
            return null;
        }

        for (int i = 0; i < breakNode.getScopeNestingLevel(); i++) {
            closeWith();
        }

        method.splitAwareGoto(breakNode.getTargetLabel());

        return null;
    }

    private MethodEmitter loadArgs(final List<Node> args) {
        return loadArgs(args, null, false, args.size());
    }

    private MethodEmitter loadArgs(final List<Node> args, final String signature, final boolean isVarArg, final int argCount) {
        // arg have already been converted to objects here.
        if (isVarArg || argCount > LinkerCallSite.ARGLIMIT) {
            loadArgsArray(args);
            return method;
        }

        // pad with undefined if size is too short. argCount is the real number of args
        int n = 0;
        final Type[] params = signature == null ? null : Type.getMethodArguments(signature);
        for (final Node arg : args) {
            assert arg != null;
            load(arg);
            if (params != null) {
                method.convert(params[n]);
            }
            n++;
            if (n >= argCount) {
                break;
            }
        }

        while (n < argCount) {
            method.loadUndefined(Type.OBJECT);
            n++;
        }

        return method;
    }

    /**
     * Create a new function object, including generating its stub code.
     *
     * @param functionNode  FunctionNode to utilize.
     */
    private void newFunctionObject(final FunctionNode functionNode) {
         // Turn thisProperties into keys and symbols for the FunctionAnalyzer
        final Map<String, Node> thisProperties = functionNode.getThisProperties();

        final List<String> keys    = new ArrayList<>();
        final List<Symbol> symbols = new ArrayList<>();

        for (final Entry<String, Node> entry : thisProperties.entrySet()) {
            keys.add(entry.getKey());
            symbols.add(entry.getValue().getSymbol());
        }

        new FunctionObjectCreator(this, functionNode, keys, symbols).makeObject(method);
    }

    @Override
    public Node enter(final CallNode callNode) {
        if (callNode.testResolved()) {
            return null;
        }

        final List<Node>   args            = callNode.getArgs();
        final Node         function        = callNode.getFunction();
        final FunctionNode currentFunction = getCurrentFunctionNode();
        final Block        currentBlock    = getCurrentBlock();

        function.accept(new NodeVisitor(compileUnit, method) {

            private void sharedScopeCall(final IdentNode identNode, final int flags) {
                final Symbol symbol = identNode.getSymbol();
                int    scopeCallFlags = flags;
                method.loadScope();
                if (symbol.isFastScope(currentFunction)) {
                    method.load(getScopeProtoDepth(currentBlock, symbol));
                    scopeCallFlags |= CALLSITE_FAST_SCOPE;
                } else {
                    method.load(-1); // Bypass fast-scope code in shared callsite
                }
                loadArgs(args);
                final Type[] paramTypes = method.getTypesFromStack(args.size());
                final SharedScopeCall scopeCall = getScopeCall(symbol, identNode.getType(), callNode.getType(), paramTypes, scopeCallFlags);
                scopeCall.generateInvoke(method);
            }

            private void scopeCall(final IdentNode node, final int flags) {
                load(node);
                method.convert(Type.OBJECT); // foo() makes no sense if foo == 3
                // ScriptFunction will see CALLSITE_SCOPE and will bind scope accordingly.
                method.loadNull();
                loadArgs(args);
                method.dynamicCall(callNode.getType(), args.size(), flags);
            }

            private void evalCall(final IdentNode node, final int flags) {
                load(node);
                method.convert(Type.OBJECT); // foo() makes no sense if foo == 3

                final Label not_eval  = new Label("not_eval");
                final Label eval_done = new Label("eval_done");

                // check if this is the real built-in eval
                method.dup();
                globalIsEval();

                method.ifeq(not_eval);
                // We don't need ScriptFunction object for 'eval'
                method.pop();

                method.loadScope(); // Load up self (scope).

                final CallNode.EvalArgs evalArgs = callNode.getEvalArgs();
                // load evaluated code
                load(evalArgs.code);
                method.convert(Type.OBJECT);
                // special/extra 'eval' arguments
                load(evalArgs.evalThis);
                method.load(evalArgs.location);
                method.load(evalArgs.strictMode);
                method.convert(Type.OBJECT);

                // direct call to Global.directEval
                globalDirectEval();
                method.convert(callNode.getType());
                method._goto(eval_done);

                method.label(not_eval);
                // This is some scope 'eval' or global eval replaced by user
                // but not the built-in ECMAScript 'eval' function call
                method.loadNull();
                loadArgs(args);
                method.dynamicCall(callNode.getType(), args.size(), flags);

                method.label(eval_done);
            }

            @Override
            public Node enter(final IdentNode node) {
                final Symbol symbol = node.getSymbol();

                if (symbol.isScope()) {
                    final int flags = getCallSiteFlags() | CALLSITE_SCOPE;
                    final int useCount = symbol.getUseCount();

                    // Threshold for generating shared scope callsite is lower for fast scope symbols because we know
                    // we can dial in the correct scope. However, we als need to enable it for non-fast scopes to
                    // support huge scripts like mandreel.js.
                    if (callNode.isEval()) {
                        evalCall(node, flags);
                    } else if (useCount <= SharedScopeCall.FAST_SCOPE_CALL_THRESHOLD
                            || (!symbol.isFastScope(currentFunction) && useCount <= SharedScopeCall.SLOW_SCOPE_CALL_THRESHOLD)
                            || callNode.inWithBlock()) {
                        scopeCall(node, flags);
                    } else {
                        sharedScopeCall(node, flags);
                    }
                    assert method.peekType().equals(callNode.getType());
                } else {
                    enterDefault(node);
                }

                return null;
            }

            @Override
            public Node enter(final AccessNode node) {
                load(node.getBase());
                method.convert(Type.OBJECT);
                method.dup();
                method.dynamicGet(node.getType(), node.getProperty().getName(), getCallSiteFlags(), true);
                method.swap();
                loadArgs(args);
                method.dynamicCall(callNode.getType(), args.size(), getCallSiteFlags());
                assert method.peekType().equals(callNode.getType());

                return null;
            }

            @Override
            public Node enter(final ReferenceNode node) {
                final FunctionNode callee   = node.getReference();
                final boolean      isVarArg = callee.isVarArg();
                final int          argCount = isVarArg ? -1 : callee.getParameters().size();

                final String signature = new FunctionSignature(true, callee.needsCallee(), callee.getReturnType(), isVarArg ? null : callee.getParameters()).toString();

                if (callee.isStrictMode()) { // self is undefined
                    method.loadUndefined(Type.OBJECT);
                } else { // get global from scope (which is the self)
                    globalInstance();
                }

                if (callee.needsCallee()) { // TODO: always true
                    newFunctionObject(callee); // TODO: if callee not needed, function object is used only to pass scope (could be optimized). if neither the scope nor the function object is needed by the callee, we can pass null instead.
                }

                loadArgs(args, signature, isVarArg, argCount);
                method.invokeStatic(callee.getCompileUnit().getUnitClassName(), callee.getName(), signature);
                assert method.peekType().equals(callee.getReturnType()) : method.peekType() + " != " + callee.getReturnType();

                return null;
            }

            @Override
            public Node enter(final IndexNode node) {
                load(node.getBase());
                method.convert(Type.OBJECT);
                method.dup();
                load(node.getIndex());
                final Type indexType = node.getIndex().getType();
                if (indexType.isObject() || indexType.isBoolean()) {
                    method.convert(Type.OBJECT); //TODO
                }
                method.dynamicGetIndex(node.getType(), getCallSiteFlags(), true);
                method.swap();
                loadArgs(args);
                method.dynamicCall(callNode.getType(), args.size(), getCallSiteFlags());
                assert method.peekType().equals(callNode.getType());

                return null;
            }

            @Override
            protected Node enterDefault(final Node node) {
                // Load up function.
                load(function);
                method.convert(Type.OBJECT); //TODO, e.g. booleans can be used as functions
                method.loadNull(); // ScriptFunction will figure out the correct this when it sees CALLSITE_SCOPE

                loadArgs(args);
                method.dynamicCall(callNode.getType(), args.size(), getCallSiteFlags() | CALLSITE_SCOPE);
                assert method.peekType().equals(callNode.getType());

                return null;
            }
        });

        method.store(callNode.getSymbol());

        return null;
    }

    @Override
    public Node enter(final ContinueNode continueNode) {
        if (continueNode.testResolved()) {
            return null;
        }

        for (int i = 0; i < continueNode.getScopeNestingLevel(); i++) {
            closeWith();
        }

        method.splitAwareGoto(continueNode.getTargetLabel());

        return null;
    }

    @Override
    public Node enter(final DoWhileNode doWhileNode) {
        return enter((WhileNode)doWhileNode);
    }

    @Override
    public Node enter(final EmptyNode emptyNode) {
        return null;
    }

    @Override
    public Node enter(final ExecuteNode executeNode) {
        if (executeNode.testResolved()) {
            return null;
        }

        final Node expression = executeNode.getExpression();
        expression.accept(this);

        return null;
    }

    @Override
    public Node enter(final ForNode forNode) {
        if (forNode.testResolved()) {
            return null;
        }

        final Node  test   = forNode.getTest();
        final Block body   = forNode.getBody();
        final Node  modify = forNode.getModify();

        final Label breakLabel    = forNode.getBreakLabel();
        final Label continueLabel = forNode.getContinueLabel();
        final Label loopLabel     = new Label("loop");

        Node init = forNode.getInit();

        if (forNode.isForIn()) {
            final Symbol iter = forNode.getIterator();

            // We have to evaluate the optional initializer expression
            // of the iterator variable of the for-in statement.
            if (init instanceof VarNode) {
                init.accept(this);
                init = ((VarNode)init).getName();
            }

            load(modify);
            assert modify.getType().isObject();
            method.invoke(forNode.isForEach() ? ScriptRuntime.TO_VALUE_ITERATOR : ScriptRuntime.TO_PROPERTY_ITERATOR);
            method.store(iter);
            method._goto(continueLabel);
            method.label(loopLabel);

            new Store<Node>(init) {
                @Override
                protected void evaluate() {
                    method.load(iter);
                    method.invoke(interfaceCallNoLookup(Iterator.class, "next", Object.class));
                }
            }.store();

            body.accept(this);

            method.label(continueLabel);
            method.load(iter);
            method.invoke(interfaceCallNoLookup(Iterator.class, "hasNext", boolean.class));
            method.ifne(loopLabel);
            method.label(breakLabel);
        } else {
            if (init != null) {
                init.accept(this);
            }

            final Label testLabel = new Label("test");

            method._goto(testLabel);
            method.label(loopLabel);
            body.accept(this);
            method.label(continueLabel);

            if (!body.isTerminal() && modify != null) {
                load(modify);
            }

            method.label(testLabel);
            if (test != null) {
                new BranchOptimizer(this, method).execute(test, loopLabel, true);
            } else {
                method._goto(loopLabel);
            }

            method.label(breakLabel);
        }

        return null;
    }

    private void initLocals(final Block block) {
        initLocals(block, null);
    }

    /**
     * Initialize the slots in a frame to undefined.
     *
     * @param block block with local vars.
     */
    private void initLocals(final Block block, final AccessSpecializer accessSpecializer) {
        final FunctionNode function       = block.getFunction();
        final boolean      isFunctionNode = block == function;

        /*
         * Get the symbols from the frame and realign the frame so that all
         * slots get correct numbers. The slot numbering is not fixed until
         * after initLocals has been run
         */
        final Frame        frame   = block.getFrame();
        final List<Symbol> symbols = frame.getSymbols();

        /* Fix the predefined slots so they have numbers >= 0, like varargs. */
        frame.realign();

        /*
         * Determine if block needs scope, if not, just do initSymbols for this block.
         */
        if (block.needsScope()) {
            /*
             * Determine if function is varargs and consequently variables have to
             * be in the scope.
             */
            final boolean isVarArg    = function.isVarArg();
            final boolean varsInScope = function.varsInScope();

            // TODO for LET we can do better: if *block* does not contain any eval/with, we don't need its vars in scope.

            final List<String> nameList = new ArrayList<>();
            final List<Symbol> locals   = new ArrayList<>();

            // note that in the case of a varargs vector the arguments in the vector should NOT have local slots
            // this was a bug: see NASHORN-21
            if (isVarArg) {
                for (final IdentNode param : function.getParameters()) {
                    param.getSymbol().setNeedsSlot(false);
                }
            }

            // If there are variable arguments, we need to load them (functions only).
            if (isFunctionNode && isVarArg) {
                method.loadVarArgs();
                method.loadCallee();
                method.load(function.getParameters().size());
                globalAllocateArguments();
                method.storeArguments();
            }

            // Initalize symbols and values
            final List<Symbol> newSymbols = new ArrayList<>();
            final List<Symbol> values     = new ArrayList<>();

            for (final Symbol symbol : symbols) {
                if (symbol.isInternal() || symbol.isThis()) {
                    continue;
                }

                if (symbol.isVar() && (varsInScope || symbol.isScope())) {
                    nameList.add(symbol.getName());
                    newSymbols.add(symbol);
                    values.add(null);
                    symbol.setIsScope();
                    symbol.setNeedsSlot(false);
                } else if (symbol.isVar()) {
                    assert symbol.hasSlot() : symbol + " should have a slot only, no scope";
                    locals.add(symbol);
                } else if (symbol.isParam() && (varsInScope || isVarArg || symbol.isScope())) {
                    nameList.add(symbol.getName());
                    newSymbols.add(symbol);
                    values.add(isVarArg ? null : symbol);
                    symbol.setIsScope();
                    if (isVarArg) {
                        symbol.setNeedsSlot(false);
                    }
                }
            }

            /* Correct slot numbering again */
            frame.realign();

            // we may have locals that need to be initialized
            initSymbols(locals);

            if (isFunctionNode) {
                initScope();
            }

            if (accessSpecializer != null) {
                ((FunctionNode)block).accept(accessSpecializer);
            }

            /*
             * Create a new object based on the symbols and values, generate
             * bootstrap code for object
             */
            final FieldObjectCreator<Symbol> foc = new FieldObjectCreator<Symbol>(this, nameList, newSymbols, values, true, isVarArg) {
                @Override
                protected Type getValueType(final Symbol value) {
                    return value.getSymbolType();
                }

                @Override
                protected void loadValue(final Symbol value) {
                    method.load(value);
                }
            };
            foc.makeObject(method);

            // runScript(): merge scope into global
            if (isFunctionNode && function.isScript()) {
                method.invoke(ScriptRuntime.MERGE_SCOPE);
            }

            method.storeScope();
        } else {
            initSymbols(symbols);

            if (isFunctionNode) {
                initScope();
            }
        }

        // Debugging: print symbols? @see --print-symbols flag
        printSymbols(block, (isFunctionNode ? "Function " : "Block in ") + (function.getIdent() == null ? "<anonymous>" : function.getIdent().getName()));
    }

    private void initScope() {
        method.loadCallee();
        method.invoke(ScriptFunction.GET_SCOPE);
        method.storeScope();
    }

    @Override
    public Node enter(final FunctionNode functionNode) {
        if (functionNode.testResolved()) {
            return null;
        }

        compileUnit = functionNode.getCompileUnit();
        assert compileUnit != null;

        method = compileUnit.getClassEmitter().method(functionNode);
        functionNode.setMethodEmitter(method);
        // Mark end for variable tables.
        method.begin();
        method.label(functionNode.getEntryLabel());

        initLocals(functionNode, new AccessSpecializer());

        return functionNode;
    }

    @Override
    public Node leave(final FunctionNode functionNode) {
        // Mark end for variable tables.
        method.label(functionNode.getBreakLabel());

        if (!functionNode.needsScope()) {
            method.markerVariable(LEAF.tag(), functionNode.getEntryLabel(), functionNode.getBreakLabel());
        }

        symbolInfo(functionNode);
        try {
            method.end(); // wrap up this method
        } catch (final Throwable t) {
            Context.printStackTrace(t);
            final VerifyError e = new VerifyError("Code generation bug in \"" + functionNode.getName() + "\": likely stack misaligned: " + t + " " + functionNode.getSource().getName());
            e.initCause(t);
            throw e;
        }

        return functionNode;
    }

    @Override
    public Node enter(final IdentNode identNode) {
        return null;
    }

    @Override
    public Node enter(final IfNode ifNode) {
        if (ifNode.testResolved()) {
            return null;
        }

        final Node  test = ifNode.getTest();
        final Block pass = ifNode.getPass();
        final Block fail = ifNode.getFail();

        final Label failLabel  = new Label("if_fail");
        final Label afterLabel = fail == null ? failLabel : new Label("if_done");

        new BranchOptimizer(this, method).execute(test, failLabel, false);

        boolean passTerminal = false;
        boolean failTerminal = false;

        pass.accept(this);
        if (!pass.hasTerminalFlags()) {
            method._goto(afterLabel); //don't fallthru to fail block
        } else {
            passTerminal = pass.isTerminal();
        }

        if (fail != null) {
            method.label(failLabel);
            fail.accept(this);
            failTerminal = fail.isTerminal();
        }

        //if if terminates, put the after label there
        if (!passTerminal || !failTerminal) {
            method.label(afterLabel);
        }

        return null;
    }

    @Override
    public Node enter(final IndexNode indexNode) {
        if (indexNode.testResolved()) {
            return null;
        }

        load(indexNode);

        return null;
    }

    @Override
    public Node enter(final LineNumberNode lineNumberNode) {
        if (lineNumberNode.testResolved()) {
            return null;
        }

        final Label label = new Label("line:" + lineNumberNode.getLineNumber() + " (" + getCurrentFunctionNode().getName() + ")");
        method.label(label);
        method.lineNumber(lineNumberNode.getLineNumber(), label);

        return null;
    }

    /**
     * Load a list of nodes as an array of a specific type
     * The array will contain the visited nodes.
     *
     * @param arrayLiteralNode the array of contents
     * @param arrayType        the type of the array, e.g. ARRAY_NUMBER or ARRAY_OBJECT
     *
     * @return the method generator that was used
     */
    private MethodEmitter loadArray(final ArrayLiteralNode arrayLiteralNode, final ArrayType arrayType) {
        assert arrayType == Type.INT_ARRAY || arrayType == Type.NUMBER_ARRAY || arrayType == Type.OBJECT_ARRAY;

        final Node[]          nodes    = arrayLiteralNode.getValue();
        final Object          presets  = arrayLiteralNode.getPresets();
        final int[]           postsets = arrayLiteralNode.getPostsets();
        final Class<?>        type     = arrayType.getTypeClass();
        final List<ArrayUnit> units    = arrayLiteralNode.getUnits();

        loadConstant(presets);

        final Type elementType = arrayType.getElementType();

        if (units != null) {
            final CompileUnit   savedCompileUnit = compileUnit;
            final MethodEmitter savedMethod      = method;

            try {
                for (final ArrayUnit unit : units) {
                    compileUnit = unit.getCompileUnit();

                    final String className = compileUnit.getUnitClassName();
                    final String name      = compiler.uniqueName(SPLIT_PREFIX.tag());
                    final String signature = methodDescriptor(type, Object.class, ScriptFunction.class, ScriptObject.class, type);

                    method = compileUnit.getClassEmitter().method(EnumSet.of(Flag.PUBLIC, Flag.STATIC), name, signature);
                    method.setFunctionNode(getCurrentFunctionNode());
                    method.begin();

                    fixScopeSlot();

                    method.load(arrayType, SPLIT_ARRAY_ARG.slot());

                    for (int i = unit.getLo(); i < unit.getHi(); i++) {
                        storeElement(nodes, elementType, postsets[i]);
                    }

                    method._return();
                    method.end();

                    savedMethod.loadThis();
                    savedMethod.swap();
                    savedMethod.loadCallee();
                    savedMethod.swap();
                    savedMethod.loadScope();
                    savedMethod.swap();
                    savedMethod.invokeStatic(className, name, signature);
                }
            } finally {
                compileUnit = savedCompileUnit;
                method      = savedMethod;
            }

            return method;
        }

        for (final int postset : postsets) {
            storeElement(nodes, elementType, postset);
        }

        return method;
    }

    private void storeElement(final Node[] nodes, final Type elementType, final int index) {
        method.dup();
        method.load(index);

        final Node element = nodes[index];

        if (element == null) {
            method.loadEmpty(elementType);
        } else {
            assert elementType.isEquivalentTo(element.getType()) : "array element type doesn't match array type";
            load(element);
        }

        method.arraystore();
    }

    private MethodEmitter loadArgsArray(final List<Node> args) {
        final Object[] array = new Object[args.size()];
        loadConstant(array);

        for (int i = 0; i < args.size(); i++) {
            method.dup();
            method.load(i);
            load(args.get(i)).convert(Type.OBJECT); //has to be upcast to object or we fail
            method.arraystore();
        }

        return method;
    }

    /**
     * Load a constant from the constant array. This is only public to be callable from the objects
     * subpackage. Do not call directly.
     *
     * @param string string to load
     */
    public void loadConstant(final String string) {
        final String       unitClassName = compileUnit.getUnitClassName();
        final ClassEmitter classEmitter  = compileUnit.getClassEmitter();
        final int          index         = compiler.getConstantData().add(string);

        method.load(index);
        method.invokeStatic(unitClassName, GET_STRING.tag(), methodDescriptor(String.class, int.class));
        classEmitter.needGetConstantMethod(String.class);
    }

    /**
     * Load a constant from the constant array. This is only public to be callable from the objects
     * subpackage. Do not call directly.
     *
     * @param object object to load
     */
    public void loadConstant(final Object object) {
        final String       unitClassName = compileUnit.getUnitClassName();
        final ClassEmitter classEmitter  = compileUnit.getClassEmitter();
        final int          index         = compiler.getConstantData().add(object);
        final Class<?>     cls           = object.getClass();

        if (cls == PropertyMap.class) {
            method.load(index);
            method.invokeStatic(unitClassName, GET_MAP.tag(), methodDescriptor(PropertyMap.class, int.class));
            classEmitter.needGetConstantMethod(PropertyMap.class);
        } else if (cls.isArray()) {
            method.load(index);
            final String methodName = ClassEmitter.getArrayMethodName(cls);
            method.invokeStatic(unitClassName, methodName, methodDescriptor(cls, int.class));
            classEmitter.needGetConstantMethod(cls);
        } else {
            method.loadConstants(unitClassName).load(index).arrayload();
            if (cls != Object.class) {
                method.checkcast(cls);
            }
        }
    }

    // literal values
    private MethodEmitter load(final LiteralNode<?> node) {
        final Object value = node.getValue();

        if (value == null) {
            method.loadNull();
        } else if (value instanceof Undefined) {
            method.loadUndefined(Type.OBJECT);
        } else if (value instanceof String) {
            final String string = (String)value;

            if (string.length() > (MethodEmitter.LARGE_STRING_THRESHOLD / 3)) { // 3 == max bytes per encoded char
                loadConstant(string);
            } else {
                method.load(string);
            }
        } else if (value instanceof RegexToken) {
            loadRegex((RegexToken)value);
        } else if (value instanceof Boolean) {
            method.load((Boolean)value);
        } else if (value instanceof Integer) {
            method.load((Integer)value);
        } else if (value instanceof Long) {
            method.load((Long)value);
        } else if (value instanceof Double) {
            method.load((Double)value);
        } else if (node instanceof ArrayLiteralNode) {
            final ArrayType type = (ArrayType)node.getType();
            loadArray((ArrayLiteralNode)node, type);
            globalAllocateArray(type);
        } else {
            assert false : "Unknown literal for " + node.getClass() + " " + value.getClass() + " " + value;
        }

        return method;
    }

    private MethodEmitter loadRegexToken(final RegexToken value) {
        method.load(value.getExpression());
        method.load(value.getOptions());
        return globalNewRegExp();
    }

    private MethodEmitter loadRegex(final RegexToken regexToken) {
        if (regexFieldCount > MAX_REGEX_FIELDS) {
            return loadRegexToken(regexToken);
        }
        // emit field
        final String       regexName    = compiler.uniqueName(REGEX_PREFIX.tag());
        final ClassEmitter classEmitter = compileUnit.getClassEmitter();

        classEmitter.field(EnumSet.of(PRIVATE, STATIC), regexName, Object.class);
        regexFieldCount++;

        // get field, if null create new regex, finally clone regex object
        method.getStatic(compileUnit.getUnitClassName(), regexName, typeDescriptor(Object.class));
        method.dup();
        final Label cachedLabel = new Label("cached");
        method.ifnonnull(cachedLabel);

        method.pop();
        loadRegexToken(regexToken);
        method.dup();
        method.putStatic(compileUnit.getUnitClassName(), regexName, typeDescriptor(Object.class));

        method.label(cachedLabel);
        globalRegExpCopy();

        return method;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Node enter(final LiteralNode literalNode) {
        load(literalNode).store(literalNode.getSymbol());
        return null;
    }

    @Override
    public Node enter(final ObjectNode objectNode) {
        if (objectNode.testResolved()) {
            return null;
        }

        final List<Node> elements = objectNode.getElements();
        final int        size     = elements.size();

        final List<String> keys    = new ArrayList<>();
        final List<Symbol> symbols = new ArrayList<>();
        final List<Node>   values  = new ArrayList<>();

        boolean hasGettersSetters = false;

        for (int i = 0; i < size; i++) {
            final PropertyNode propertyNode = (PropertyNode)elements.get(i);
            final Node         value        = propertyNode.getValue();
            final String       key          = propertyNode.getKeyName();
            final Symbol       symbol       = value == null ? null : propertyNode.getSymbol();

            if (value == null) {
                hasGettersSetters = true;
            }

            keys.add(key);
            symbols.add(symbol);
            values.add(value);
        }

        new FieldObjectCreator<Node>(this, keys, symbols, values) {
            @Override
            protected Type getValueType(final Node node) {
                return node.getType();
            }

            @Override
            protected void loadValue(final Node node) {
                load(node);
            }

            /**
             * Ensure that the properties start out as object types so that
             * we can do putfield initializations instead of dynamicSetIndex
             * which would be the case to determine initial property type
             * otherwise.
             *
             * Use case, it's very expensive to do a million var x = {a:obj, b:obj}
             * just to have to invalidate them immediately on initialization
             *
             * see NASHORN-594
             */
            @Override
            protected MapCreator newMapCreator(final Class<?> fieldObjectClass) {
                return new ObjectMapCreator(fieldObjectClass, keys, symbols);
            }

        }.makeObject(method);

        method.dup();
        globalObjectPrototype();
        method.invoke(ScriptObject.SET_PROTO);

        if (!hasGettersSetters) {
            method.store(objectNode.getSymbol());
            return null;
        }

        for (final Node element : elements) {
            final PropertyNode  propertyNode = (PropertyNode)element;
            final Object        key          = propertyNode.getKey();
            final ReferenceNode getter       = (ReferenceNode)propertyNode.getGetter();
            final ReferenceNode setter       = (ReferenceNode)propertyNode.getSetter();

            if (getter == null && setter == null) {
                continue;
            }

            method.dup().loadKey(key);

            if (getter == null) {
                method.loadNull();
            } else {
                getter.accept(this);
            }

            if (setter == null) {
                method.loadNull();
            } else {
                setter.accept(this);
            }

            method.invoke(ScriptObject.SET_USER_ACCESSORS);
        }

        method.store(objectNode.getSymbol());

        return null;
    }

    @Override
    public Node enter(final ReferenceNode referenceNode) {
        if (referenceNode.testResolved()) {
            return null;
        }

        newFunctionObject(referenceNode.getReference());

        return null;
    }

    @Override
    public Node enter(final ReturnNode returnNode) {
        if (returnNode.testResolved()) {
            return null;
        }

        // Set the split return flag in the scope if this is a split method fragment.
        if (method.getSplitNode() != null) {
            assert method.getSplitNode().hasReturn() : "unexpected return in split node";

            method.loadScope();
            method.checkcast(Scope.class);
            method.load(0);
            method.invoke(Scope.SET_SPLIT_STATE);
        }

        final Node expression = returnNode.getExpression();
        if (expression != null) {
            load(expression);
        } else {
            method.loadUndefined(getCurrentFunctionNode().getReturnType());
        }

        method._return(getCurrentFunctionNode().getReturnType());

        return null;
    }

    private static boolean isNullLiteral(final Node node) {
        return node instanceof LiteralNode<?> && ((LiteralNode<?>) node).isNull();
    }

    private boolean nullCheck(final RuntimeNode runtimeNode, final List<Node> args, final String signature) {
        final Request request = runtimeNode.getRequest();

        if (!Request.isEQ(request) && !Request.isNE(request)) {
            return false;
        }

        assert args.size() == 2 : "EQ or NE or TYPEOF need two args";

        Node lhs = args.get(0);
        Node rhs = args.get(1);

        if (isNullLiteral(lhs)) {
            final Node tmp = lhs;
            lhs = rhs;
            rhs = tmp;
        }

        if (isNullLiteral(rhs)) {
            final Label trueLabel  = new Label("trueLabel");
            final Label falseLabel = new Label("falseLabel");
            final Label endLabel   = new Label("end");

            load(lhs);
            method.dup();
            if (Request.isEQ(request)) {
                method.ifnull(trueLabel);
            } else if (Request.isNE(request)) {
                method.ifnonnull(trueLabel);
            } else {
                assert false : "Invalid request " + request;
            }

            method.label(falseLabel);
            load(rhs);
            method.invokeStatic(CompilerConstants.className(ScriptRuntime.class), request.toString(), signature);
            method._goto(endLabel);

            method.label(trueLabel);
            // if NE (not strict) this can be "undefined != null" which is supposed to be false
            if (request == Request.NE) {
                method.loadUndefined(Type.OBJECT);
                final Label isUndefined = new Label("isUndefined");
                final Label afterUndefinedCheck = new Label("afterUndefinedCheck");
                method.if_acmpeq(isUndefined);
                // not undefined
                method.load(true);
                method._goto(afterUndefinedCheck);
                method.label(isUndefined);
                method.load(false);
                method.label(afterUndefinedCheck);
            } else {
                method.pop();
                method.load(true);
            }
            method.label(endLabel);
            method.convert(runtimeNode.getType());
            method.store(runtimeNode.getSymbol());

            return true;
        }

        return false;
    }

    private boolean specializationCheck(final RuntimeNode.Request request, final Node node, final List<Node> args) {
        if (!request.canSpecialize()) {
            return false;
        }

        assert args.size() == 2;
        final Node lhs = args.get(0);
        final Node rhs = args.get(1);

        final Type returnType = node.getType();
        load(lhs);
        load(rhs);

        Request finalRequest = request;

        final Request reverse = Request.reverse(request);
        if (method.peekType().isObject() && reverse != null) {
            if (!method.peekType(1).isObject()) {
                method.swap();
                finalRequest = reverse;
            }
        }

        method.dynamicRuntimeCall(
                new SpecializedRuntimeNode(
                    finalRequest,
                    new Type[] {
                        method.peekType(1),
                        method.peekType()
                    },
                    returnType).getInitialName(),
                returnType,
                finalRequest);

        method.convert(node.getType());
        method.store(node.getSymbol());

        return true;
    }

    @Override
    public Node enter(final RuntimeNode runtimeNode) {
        if (runtimeNode.testResolved()) {
            return null;
        }

        /*
         * First check if this should be something other than a runtime node
         * AccessSpecializer might have changed the type
         */
        if (runtimeNode.isPrimitive()) {

            final Node lhs = runtimeNode.getArgs().get(0);
            Node rhs = null;

            if (runtimeNode.getArgs().size() > 1) {
                rhs = runtimeNode.getArgs().get(1);
            }

            final Type   type   = runtimeNode.getType();
            final Symbol symbol = runtimeNode.getSymbol();

            switch (runtimeNode.getRequest()) {
            case EQ:
            case EQ_STRICT:
                return enterCmp(lhs, rhs, Condition.EQ, type, symbol);
            case NE:
            case NE_STRICT:
                return enterCmp(lhs, rhs, Condition.NE, type, symbol);
            case LE:
                return enterCmp(lhs, rhs, Condition.LE, type, symbol);
            case LT:
                return enterCmp(lhs, rhs, Condition.LT, type, symbol);
            case GE:
                return enterCmp(lhs, rhs, Condition.GE, type, symbol);
            case GT:
                return enterCmp(lhs, rhs, Condition.GT, type, symbol);
            case ADD:
                return enterNumericAdd(lhs, rhs, type, symbol);
            default:
                // it's ok to send this one on with only primitive arguments, maybe INSTANCEOF(true, true) or similar
                // assert false : runtimeNode + " has all primitive arguments. This is an inconsistent state";
                break;
            }
        }

        // Get the request arguments.
        final List<Node> args = runtimeNode.getArgs();

        if (nullCheck(runtimeNode, args, new FunctionSignature(false, runtimeNode.getType(), args).toString())) {
            return null;
        }

        if (specializationCheck(runtimeNode.getRequest(), runtimeNode, args)) {
            return null;
        }

        for (final Node arg : runtimeNode.getArgs()) {
            load(arg).convert(Type.OBJECT); //TODO this should not be necessary below Lower
        }

        method.invokeStatic(
            CompilerConstants.className(ScriptRuntime.class),
            runtimeNode.getRequest().toString(),
            new FunctionSignature(
                false,
                runtimeNode.getType(),
                runtimeNode.getArgs().size()).toString());
        method.convert(runtimeNode.getType());
        method.store(runtimeNode.getSymbol());

        return null;
    }

    @Override
    public Node enter(final SplitNode splitNode) {
        if (splitNode.testResolved()) {
            return null;
        }

        final CompileUnit splitCompileUnit = splitNode.getCompileUnit();

        final FunctionNode fn   = getCurrentFunctionNode();
        final String className  = splitCompileUnit.getUnitClassName();
        final String name       = splitNode.getName();

        final Class<?>   rtype  = fn.getReturnType().getTypeClass();
        final Class<?>[] ptypes = fn.isVarArg() ?
                new Class<?>[] {Object.class, ScriptFunction.class, ScriptObject.class, Object.class} :
                new Class<?>[] {Object.class, ScriptFunction.class, ScriptObject.class};

        setCurrentCompileUnit(splitCompileUnit);
        splitNode.setCompileUnit(splitCompileUnit);

        final Call splitCall = staticCallNoLookup(
            className,
            name,
            methodDescriptor(rtype, ptypes));

        setCurrentMethodEmitter(
            splitCompileUnit.getClassEmitter().method(
                EnumSet.of(Flag.PUBLIC, Flag.STATIC),
                name,
                rtype,
                ptypes));

        method.setFunctionNode(fn);
        method.setSplitNode(splitNode);
        splitNode.setMethodEmitter(method);

        final MethodEmitter caller = splitNode.getCaller();
        caller.loadThis();
        caller.loadCallee();
        caller.loadScope();
        if (fn.isVarArg()) {
            caller.loadArguments();
        }
        caller.invoke(splitCall);
        caller.storeResult();

        method.begin();

        method.loadUndefined(fn.getReturnType());
        method.storeResult();

        fixScopeSlot();

        return splitNode;
    }

    private void fixScopeSlot() {
        if (getCurrentFunctionNode().getScopeNode().getSymbol().getSlot() != SCOPE.slot()) {
            // TODO hack to move the scope to the expected slot (that's needed because split methods reuse the same slots as the root method)
            method.load(Type.typeFor(ScriptObject.class), SCOPE.slot());
            method.storeScope();
        }
    }

    @Override
    public Node leave(final SplitNode splitNode) {
        try {
            // Wrap up this method.
            method.loadResult();
            method._return(getCurrentFunctionNode().getReturnType());
            method.end();
        } catch (final Throwable t) {
            Context.printStackTrace(t);
            final VerifyError e = new VerifyError("Code generation bug in \"" + splitNode.getName() + "\": likely stack misaligned: " + t + " " + compiler.getSource().getName());
            e.initCause(t);
            throw e;
        }

        // Handle return from split method if there was one.
        final MethodEmitter caller      = splitNode.getCaller();
        final List<Label>   targets     = splitNode.getExternalTargets();
        final int           targetCount = targets.size();

        if (splitNode.hasReturn() || targetCount > 0) {

            caller.loadScope();
            caller.checkcast(Scope.class);
            caller.invoke(Scope.GET_SPLIT_STATE);

            // Split state is -1 for no split state, 0 for return, 1..n+1 for break/continue
            final Label   breakLabel = new Label("no_split_state");
            final int     low        = splitNode.hasReturn() ? 0 : 1;
            final int     labelCount = targetCount + 1 - low;
            final Label[] labels     = new Label[labelCount];

            for (int i = 0; i < labelCount; i++) {
                labels[i] = new Label("split_state_" + i);
            }

            caller.tableSwitch(low, targetCount, breakLabel, labels);
            for (int i = low; i <= targetCount; i++) {
                caller.label(labels[i - low]);
                if (i == 0) {
                    caller.loadResult();
                    caller._return(getCurrentFunctionNode().getReturnType());
                } else {
                    // Clear split state.
                    caller.loadScope();
                    caller.checkcast(Scope.class);
                    caller.load(-1);
                    caller.invoke(Scope.SET_SPLIT_STATE);
                    caller.splitAwareGoto(targets.get(i - 1));
                }
            }

            caller.label(breakLabel);
        }

        return splitNode;
    }

    @Override
    public Node enter(final SwitchNode switchNode) {
        if (switchNode.testResolved()) {
            return null;
        }

        final Node           expression  = switchNode.getExpression();
        final Symbol         tag         = switchNode.getTag();
        final boolean        allInteger  = tag.getSymbolType().isInteger();
        final List<CaseNode> cases       = switchNode.getCases();
        final CaseNode       defaultCase = switchNode.getDefaultCase();
        final Label          breakLabel  = switchNode.getBreakLabel();

        Label defaultLabel = breakLabel;
        boolean hasDefault = false;

        if (defaultCase != null) {
            defaultLabel = defaultCase.getEntry();
            hasDefault = true;
        }

        if (cases.isEmpty()) {
            method.label(breakLabel);
            return null;
        }

        if (allInteger) {
            // Tree for sorting values.
            final TreeMap<Integer, Label> tree = new TreeMap<>();

            // Build up sorted tree.
            for (final CaseNode caseNode : cases) {
                final Node test = caseNode.getTest();

                if (test != null) {
                    final Integer value = (Integer)((LiteralNode<?>) test).getValue();
                    final Label   entry = caseNode.getEntry();

                    // Take first duplicate.
                    if (!(tree.containsKey(value))) {
                        tree.put(value, entry);
                    }
                }
            }

            // Copy values and labels to arrays.
            final int       size   = tree.size();
            final Integer[] values = tree.keySet().toArray(new Integer[size]);
            final Label[]   labels = tree.values().toArray(new Label[size]);

            // Discern low, high and range.
            final int lo    = values[0];
            final int hi    = values[size - 1];
            final int range = hi - lo + 1;

            // Find an unused value for default.
            int deflt = Integer.MIN_VALUE;
            for (final int value : values) {
                if (deflt == value) {
                    deflt++;
                } else if (deflt < value) {
                    break;
                }
            }

            // Load switch expression.
            load(expression);
            final Type type = expression.getType();

            // If expression not int see if we can convert, if not use deflt to trigger default.
            if (!type.isInteger()) {
                method.load(deflt);
                method.invoke(staticCallNoLookup(ScriptRuntime.class, "switchTagAsInt", int.class, type.getTypeClass(), int.class));
            }

            // If reasonable size and not too sparse (80%), use table otherwise use lookup.
            if (range > 0 && range < 4096 && range < (size * 5 / 4)) {
                final Label[] table = new Label[range];
                Arrays.fill(table, defaultLabel);

                for (int i = 0; i < size; i++) {
                    final int value = values[i];
                    table[value - lo] = labels[i];
                }

                method.tableSwitch(lo, hi, defaultLabel, table);
            } else {
                final int[] ints = new int[size];

                for (int i = 0; i < size; i++) {
                    ints[i] = values[i];
                }

                method.lookupSwitch(defaultLabel, ints, labels);
            }
        } else {
            load(expression);

            if (expression.getType().isInteger()) {
                method.convert(Type.NUMBER).dup();
                method.store(tag);
                method.conditionalJump(MethodEmitter.Condition.NE, true, defaultLabel);
            } else {
                method.store(tag);
            }

            for (final CaseNode caseNode : cases) {
                final Node test = caseNode.getTest();

                if (test != null) {
                    method.load(tag);
                    load(test);
                    method.invoke(ScriptRuntime.EQ_STRICT);
                    method.ifne(caseNode.getEntry());
                }
            }

            method._goto(hasDefault ? defaultLabel : breakLabel);
        }

        for (final CaseNode caseNode : cases) {
            method.label(caseNode.getEntry());
            caseNode.getBody().accept(this);
        }

        if (!switchNode.isTerminal()) {
            method.label(breakLabel);
        }

        return null;
    }

    @Override
    public Node enter(final ThrowNode throwNode) {
        if (throwNode.testResolved()) {
            return null;
        }

        method._new(ECMAException.class).dup();

        final Node   expression = throwNode.getExpression();
        final Source source     = compiler.getSource();
        final int    position   = throwNode.position();
        final int    line       = source.getLine(position);
        final int    column     = source.getColumn(position);

        load(expression);
        assert expression.getType().isObject();

        method.load(source.getName());
        method.load(line);
        method.load(column);
        method.invoke(ECMAException.THROW_INIT);

        method.athrow();

        return null;
    }

    @Override
    public Node enter(final TryNode tryNode) {
        if (tryNode.testResolved()) {
            return null;
        }

        final Block       body        = tryNode.getBody();
        final List<Block> catchBlocks = tryNode.getCatchBlocks();
        final Symbol      symbol      = tryNode.getException();
        final Label       entry       = new Label("try");
        final Label       recovery    = new Label("catch");
        final Label       exit        = tryNode.getExit();
        final Label       skip        = new Label("skip");

        method.label(entry);

        body.accept(this);

        if (!body.hasTerminalFlags()) {
            method._goto(skip);
        }

        method.label(exit);

        method._catch(recovery);
        method.store(symbol);

        for (int i = 0; i < catchBlocks.size(); i++) {
            final Block saveBlock = getCurrentBlock();
            final Block catchBlock = catchBlocks.get(i);

            setCurrentBlock(catchBlock);

            try {
                enter(catchBlock);

                final CatchNode catchNode          = (CatchNode)catchBlocks.get(i).getStatements().get(0);
                final IdentNode exception          = catchNode.getException();
                final Node      exceptionCondition = catchNode.getExceptionCondition();
                final Block     catchBody          = catchNode.getBody();

                if (catchNode.isSyntheticRethrow()) {
                    // Generate catch body (inlined finally) and rethrow exception
                    catchBody.accept(this);
                    method.load(symbol).athrow();
                    continue;
                }

                new Store<IdentNode>(exception) {
                    @Override
                    protected void evaluate() {
                        /*
                         * If caught object is an instance of ECMAException, then
                         * bind obj.thrown to the script catch var. Or else bind the
                         * caught object itself to the script catch var.
                         */
                        final Label notEcmaException = new Label("no_ecma_exception");

                        method.load(symbol).dup()._instanceof(ECMAException.class).ifeq(notEcmaException);
                        method.checkcast(ECMAException.class); //TODO is this necessary?
                        method.getField(ECMAException.THROWN);
                        method.label(notEcmaException);
                    }
                }.store();

                final Label next;

                if (exceptionCondition != null) {
                    next = new Label("next");
                    load(exceptionCondition).convert(Type.BOOLEAN).ifeq(next);
                } else {
                    next = null;
                }

                catchBody.accept(this);

                if (i + 1 != catchBlocks.size() && !catchBody.hasTerminalFlags()) {
                    method._goto(skip);
                }

                if (next != null) {
                    if (i + 1 == catchBlocks.size()) {
                        // no next catch block - rethrow if condition failed
                        method._goto(skip);
                        method.label(next);
                        method.load(symbol).athrow();
                    } else {
                        method.label(next);
                    }
                }

                leave(catchBlock);
            } finally {
                setCurrentBlock(saveBlock);
            }
        }

        method.label(skip);
        method._try(entry, exit, recovery, Throwable.class);

        // Finally body is always inlined elsewhere so it doesn't need to be emitted

        return null;
    }

    @Override
    public Node enter(final VarNode varNode) {
        final Node init = varNode.getInit();

        if (varNode.testResolved() || init == null) {
            return null;
        }

        final Symbol varSymbol = varNode.getSymbol();

        assert varSymbol != null : "variable node " + varNode + " requires a symbol";

        assert method != null;

        final boolean needsScope = varSymbol.isScope();
        if (needsScope) {
            method.loadScope();
        }

        load(init);

        if (needsScope) {
            int flags = CALLSITE_SCOPE | getCallSiteFlags();
            if (varNode.isFunctionVarNode()) {
                flags |= CALLSITE_FUNCTION_DECLARATION;
            }
            final IdentNode identNode = varNode.getName();
            final Type type = identNode.getType();
            if(varSymbol.isFastScope(getCurrentFunctionNode())) {
                storeFastScopeVar(type, varSymbol, flags);
            } else {
                method.dynamicSet(type, identNode.getName(), flags);
            }
        } else {
            assert varNode.getType() == varNode.getName().getType() : "varNode type=" + varNode.getType() + " nametype=" + varNode.getName().getType() + " inittype=" + init.getType();

            method.convert(varNode.getType()); // aw: convert moved here
            method.store(varSymbol);
        }

        return null;
    }

    @Override
    public Node enter(final WhileNode whileNode) {
        if (whileNode.testResolved()) {
            return null;
        }

        final Node  test          = whileNode.getTest();
        final Block body          = whileNode.getBody();
        final Label breakLabel    = whileNode.getBreakLabel();
        final Label continueLabel = whileNode.getContinueLabel();
        final Label loopLabel     = new Label("loop");

        if (!(whileNode instanceof DoWhileNode)) {
            method._goto(continueLabel);
        }

        method.label(loopLabel);
        body.accept(this);
        if (!whileNode.isTerminal()) {
            method.label(continueLabel);
            new BranchOptimizer(this, method).execute(test, loopLabel, true);
            method.label(breakLabel);
        }

        return null;
    }

    private void closeWith() {
        method.loadScope();
        method.invoke(ScriptRuntime.CLOSE_WITH);
        method.storeScope();
    }

    @Override
    public Node enter(final WithNode withNode) {
        if (withNode.testResolved()) {
            return null;
        }

        final Node expression = withNode.getExpression();
        final Node body       = withNode.getBody();

        final Label tryLabel   = new Label("with_try");
        final Label endLabel   = new Label("with_end");
        final Label catchLabel = new Label("with_catch");
        final Label exitLabel  = new Label("with_exit");

        method.label(tryLabel);

        method.loadScope();
        load(expression);

        assert expression.getType().isObject() : "with expression needs to be object: " + expression;

        method.invoke(ScriptRuntime.OPEN_WITH);
        method.storeScope();

        body.accept(this);

        if (!body.isTerminal()) {
            closeWith();
            method._goto(exitLabel);
        }

        method.label(endLabel);

        method._catch(catchLabel);
        closeWith();
        method.athrow();

        method.label(exitLabel);

        method._try(tryLabel, endLabel, catchLabel);

        return null;
    }

    @Override
    public Node enterADD(final UnaryNode unaryNode) {
        if (unaryNode.testResolved()) {
            return null;
        }

        load(unaryNode.rhs());
        assert unaryNode.rhs().getType().isNumber();
        method.store(unaryNode.getSymbol());

        return null;
    }

    @Override
    public Node enterBIT_NOT(final UnaryNode unaryNode) {
        if (unaryNode.testResolved()) {
            return null;
        }

        load(unaryNode.rhs()).convert(Type.INT).load(-1).xor().store(unaryNode.getSymbol());

        return null;
    }

    // do this better with convert calls to method. TODO
    @Override
    public Node enterCONVERT(final UnaryNode unaryNode) {
        if (unaryNode.testResolved()) {
            return null;
        }

        final Node rhs = unaryNode.rhs();
        final Type to  = unaryNode.getType();

        if (to.isObject() && rhs instanceof LiteralNode) {
            final LiteralNode<?> literalNode = (LiteralNode<?>)rhs;
            final Object value = literalNode.getValue();

            if (value instanceof Number) {
                assert !to.isArray() : "type hygiene - cannot convert number to array: (" + to.getTypeClass().getSimpleName() + ')' + value;
                if (value instanceof Integer) {
                    method.load((Integer)value);
                } else if (value instanceof Long) {
                    method.load((Long)value);
                } else if (value instanceof Double) {
                    method.load((Double)value);
                } else {
                    assert false;
                }
                method.convert(Type.OBJECT);
            } else if (value instanceof Boolean) {
                method.getField(staticField(Boolean.class, value.toString().toUpperCase(), Boolean.class));
            } else {
                load(rhs);
                method.convert(unaryNode.getType());
            }
        } else {
            load(rhs);
            method.convert(unaryNode.getType());
        }

        method.store(unaryNode.getSymbol());

        return null;
    }

    @Override
    public Node enterDECINC(final UnaryNode unaryNode) {
        if (unaryNode.testResolved()) {
            return null;
        }

        final Node      rhs         = unaryNode.rhs();
        final Type      type        = unaryNode.getType();
        final TokenType tokenType   = unaryNode.tokenType();
        final boolean   isPostfix   = tokenType == TokenType.DECPOSTFIX || tokenType == TokenType.INCPOSTFIX;
        final boolean   isIncrement = tokenType == TokenType.INCPREFIX || tokenType == TokenType.INCPOSTFIX;

        assert !type.isObject();

        new SelfModifyingStore<UnaryNode>(unaryNode, rhs) {

            @Override
            protected void evaluate() {
                load(rhs, true);

                method.convert(type);
                if (!isPostfix) {
                    if (type.isInteger()) {
                        method.load(isIncrement ? 1 : -1);
                    } else if (type.isLong()) {
                        method.load(isIncrement ? 1L : -1L);
                    } else {
                        method.load(isIncrement ? 1.0 : -1.0);
                    }
                    method.add();
                }
            }

            @Override
            protected void storeNonDiscard() {
                super.storeNonDiscard();
                if (isPostfix) {
                    if (type.isInteger()) {
                        method.load(isIncrement ? 1 : -1);
                    } else if (type.isLong()) {
                        method.load(isIncrement ? 1L : 1L);
                    } else {
                        method.load(isIncrement ? 1.0 : -1.0);
                    }
                    method.add();
                }
            }
        }.store();

        return null;
    }

    @Override
    public Node enterDISCARD(final UnaryNode unaryNode) {
        if (unaryNode.testResolved()) {
            return null;
        }

        final Node rhs = unaryNode.rhs();

        load(rhs);

        if (rhs.shouldDiscard()) {
            method.pop();
        }

        return null;
    }

    @Override
    public Node enterNEW(final UnaryNode unaryNode) {
        if (unaryNode.testResolved()) {
            return null;
        }

        final CallNode callNode = (CallNode)unaryNode.rhs();
        final List<Node> args   = callNode.getArgs();

        // Load function reference.
        load(callNode.getFunction()).convert(Type.OBJECT); // must detect type error

        // Load arguments.
        loadArgs(args);

        method.dynamicNew(args.size(), getCallSiteFlags());
        method.store(unaryNode.getSymbol());

        return null;
    }

    @Override
    public Node enterNOT(final UnaryNode unaryNode) {
        if (unaryNode.testResolved()) {
            return null;
        }

        final Node rhs = unaryNode.rhs();

        load(rhs);

        final Label trueLabel  = new Label("true");
        final Label afterLabel = new Label("after");

        method.convert(Type.BOOLEAN);
        method.ifne(trueLabel);
        method.load(true);
        method._goto(afterLabel);
        method.label(trueLabel);
        method.load(false);
        method.label(afterLabel);
        method.store(unaryNode.getSymbol());

        return null;
    }

    @Override
    public Node enterSUB(final UnaryNode unaryNode) {
        if (unaryNode.testResolved()) {
            return null;
        }

        load(unaryNode.rhs()).neg().store(unaryNode.getSymbol());

        return null;
    }

    private Node enterNumericAdd(final Node lhs, final Node rhs, final Type type, final Symbol symbol) {
        assert lhs.getType().equals(rhs.getType()) && lhs.getType().equals(type);
        load(lhs);
        load(rhs);
        method.add();
        method.store(symbol);

        return null;
    }

    @Override
    public Node enterADD(final BinaryNode binaryNode) {
        if (binaryNode.testResolved()) {
            return null;
        }

        final Node lhs = binaryNode.lhs();
        final Node rhs = binaryNode.rhs();

        final Type type = binaryNode.getType();
        if (type.isNumeric()) {
            enterNumericAdd(lhs, rhs, type, binaryNode.getSymbol());
        } else {
            load(lhs).convert(Type.OBJECT);
            load(rhs).convert(Type.OBJECT);
            method.add();
            method.store(binaryNode.getSymbol());
        }

        return null;
    }

    private Node enterAND_OR(final BinaryNode binaryNode) {
        if (binaryNode.testResolved()) {
            return null;
        }

        final Node lhs = binaryNode.lhs();
        final Node rhs = binaryNode.rhs();

        final Label skip = new Label("skip");

        load(lhs).convert(Type.OBJECT).dup().convert(Type.BOOLEAN);

        if (binaryNode.tokenType() == TokenType.AND) {
            method.ifeq(skip);
        } else {
            method.ifne(skip);
        }

        method.pop();
        load(rhs).convert(Type.OBJECT);
        method.label(skip);
        method.store(binaryNode.getSymbol());

        return null;
    }

    @Override
    public Node enterAND(final BinaryNode binaryNode) {
        return enterAND_OR(binaryNode);
    }

    @Override
    public Node enterASSIGN(final BinaryNode binaryNode) {
        if (binaryNode.testResolved()) {
            return null;
        }

        final Node lhs = binaryNode.lhs();
        final Node rhs = binaryNode.rhs();

        final Type lhsType = lhs.getType();
        final Type rhsType = rhs.getType();

        if (!lhsType.isEquivalentTo(rhsType)) {
            //this is OK if scoped, only locals are wrong
            assert !(lhs instanceof IdentNode) || lhs.getSymbol().isScope() : new ASTWriter(binaryNode);
        }

        new Store<BinaryNode>(binaryNode, lhs) {
            @Override
            protected void evaluate() {
                load(rhs);
            }
        }.store();

        return null;
    }

    /**
     * Helper class for assignment ops, e.g. *=, += and so on..
     */
    private abstract class AssignOp extends SelfModifyingStore<BinaryNode> {

        /** The type of the resulting operation */
        private final Type opType;

        /**
         * Constructor
         *
         * @param node the assign op node
         */
        AssignOp(final BinaryNode node) {
            this(node.getType(), node);
        }

        /**
         * Constructor
         *
         * @param opType type of the computation - overriding the type of the node
         * @param node the assign op node
         */
        AssignOp(final Type opType, final BinaryNode node) {
            super(node, node.lhs());
            this.opType = opType;
        }

        @Override
        public void store() {
            if (assignNode.testResolved()) {
                return;
            }
            super.store();
        }

        protected abstract void op();

        @Override
        protected void evaluate() {
            load(assignNode.lhs(), true).convert(opType);
            load(assignNode.rhs()).convert(opType);
            op();
            method.convert(assignNode.getType());
        }
    }

    @Override
    public Node enterASSIGN_ADD(final BinaryNode binaryNode) {
        assert RuntimeNode.Request.ADD.canSpecialize();
        final boolean specialize = binaryNode.getType() == Type.OBJECT;

        new AssignOp(binaryNode) {
            @Override
            protected boolean isSelfModifying() {
                return !specialize;
            }

            @Override
            protected void op() {
                method.add();
            }

            @Override
            protected void evaluate() {
                if (specialize && specializationCheck(Request.ADD, assignNode, Arrays.asList(assignNode.lhs(), assignNode.rhs()))) {
                    return;
                }
                super.evaluate();
            }
        }.store();

        return null;
    }

    @Override
    public Node enterASSIGN_BIT_AND(final BinaryNode binaryNode) {
        new AssignOp(Type.INT, binaryNode) {
            @Override
            protected void op() {
                method.and();
            }
        }.store();

        return null;
    }

    @Override
    public Node enterASSIGN_BIT_OR(final BinaryNode binaryNode) {
        new AssignOp(Type.INT, binaryNode) {
            @Override
            protected void op() {
                method.or();
            }
        }.store();

        return null;
    }

    @Override
    public Node enterASSIGN_BIT_XOR(final BinaryNode binaryNode) {
        new AssignOp(Type.INT, binaryNode) {
            @Override
            protected void op() {
                method.xor();
            }
        }.store();

        return null;
    }

    @Override
    public Node enterASSIGN_DIV(final BinaryNode binaryNode) {
        new AssignOp(binaryNode) {
            @Override
            protected void op() {
                method.div();
            }
        }.store();

        return null;
    }

    @Override
    public Node enterASSIGN_MOD(final BinaryNode binaryNode) {
        new AssignOp(binaryNode) {
            @Override
            protected void op() {
                method.rem();
            }
        }.store();

        return null;
    }

    @Override
    public Node enterASSIGN_MUL(final BinaryNode binaryNode) {
        new AssignOp(binaryNode) {
            @Override
            protected void op() {
                method.mul();
            }
        }.store();

        return null;
    }

    @Override
    public Node enterASSIGN_SAR(final BinaryNode binaryNode) {
        new AssignOp(Type.INT, binaryNode) {
            @Override
            protected void op() {
                method.sar();
            }
        }.store();

        return null;
    }

    @Override
    public Node enterASSIGN_SHL(final BinaryNode binaryNode) {
        new AssignOp(Type.INT, binaryNode) {
            @Override
            protected void op() {
                method.shl();
            }
        }.store();

        return null;
    }

    @Override
    public Node enterASSIGN_SHR(final BinaryNode binaryNode) {
        new AssignOp(Type.INT, binaryNode) {
            @Override
            protected void op() {
                method.shr();
                method.convert(Type.LONG).load(0xffff_ffffL).and();
            }
        }.store();

        return null;
    }

    @Override
    public Node enterASSIGN_SUB(final BinaryNode binaryNode) {
        new AssignOp(binaryNode) {
            @Override
            protected void op() {
                method.sub();
            }
        }.store();

        return null;
    }

    /**
     * Helper class for binary arithmetic ops
     */
    private abstract class BinaryArith {

        protected abstract void op();

        protected void evaluate(final BinaryNode node) {
            if (node.testResolved()) {
                return;
            }
            load(node.lhs());
            load(node.rhs());
            op();
            method.store(node.getSymbol());
        }
    }

    @Override
    public Node enterBIT_AND(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.and();
            }
        }.evaluate(binaryNode);

        return null;
    }

    @Override
    public Node enterBIT_OR(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.or();
            }
        }.evaluate(binaryNode);

        return null;
    }

    @Override
    public Node enterBIT_XOR(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.xor();
            }
        }.evaluate(binaryNode);

        return null;
    }

    private Node enterComma(final BinaryNode binaryNode) {
        if (binaryNode.testResolved()) {
            return null;
        }

        final Node lhs = binaryNode.lhs();
        final Node rhs = binaryNode.rhs();

        load(lhs);
        load(rhs);
        method.store(binaryNode.getSymbol());

        return null;
    }

    @Override
    public Node enterCOMMARIGHT(final BinaryNode binaryNode) {
        return enterComma(binaryNode);
    }

    @Override
    public Node enterCOMMALEFT(final BinaryNode binaryNode) {
        return enterComma(binaryNode);
    }

    @Override
    public Node enterDIV(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.div();
            }
        }.evaluate(binaryNode);

        return null;
    }

    private Node enterCmp(final Node lhs, final Node rhs, final Condition cond, final Type type, final Symbol symbol) {
        final Type lhsType = lhs.getType();
        final Type rhsType = rhs.getType();

        final Type widest = Type.widest(lhsType, rhsType);
        assert widest.isNumeric() || widest.isBoolean() : widest;

        load(lhs);
        method.convert(widest);
        load(rhs);
        method.convert(widest);

        final Label trueLabel  = new Label("trueLabel");
        final Label afterLabel = new Label("skip");

        method.conditionalJump(cond, trueLabel);

        method.load(Boolean.FALSE);
        method._goto(afterLabel);
        method.label(trueLabel);
        method.load(Boolean.TRUE);
        method.label(afterLabel);

        method.convert(type);
        method.store(symbol);

        return null;
    }

    private Node enterCmp(final BinaryNode binaryNode, final Condition cond) {
        if (binaryNode.testResolved()) {
            return null;
        }
        return enterCmp(binaryNode.lhs(), binaryNode.rhs(), cond, binaryNode.getType(), binaryNode.getSymbol());
    }

    @Override
    public Node enterEQ(final BinaryNode binaryNode) {
        return enterCmp(binaryNode, Condition.EQ);
    }

    @Override
    public Node enterEQ_STRICT(final BinaryNode binaryNode) {
        return enterCmp(binaryNode, Condition.EQ);
    }

    @Override
    public Node enterGE(final BinaryNode binaryNode) {
        return enterCmp(binaryNode, Condition.GE);
    }

    @Override
    public Node enterGT(final BinaryNode binaryNode) {
        return enterCmp(binaryNode, Condition.GT);
    }

    @Override
    public Node enterLE(final BinaryNode binaryNode) {
        return enterCmp(binaryNode, Condition.LE);
    }

    @Override
    public Node enterLT(final BinaryNode binaryNode) {
        return enterCmp(binaryNode, Condition.LT);
    }

    @Override
    public Node enterMOD(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.rem();
            }
        }.evaluate(binaryNode);

        return null;
    }

    @Override
    public Node enterMUL(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.mul();
            }
        }.evaluate(binaryNode);

        return null;
    }

    @Override
    public Node enterNE(final BinaryNode binaryNode) {
        return enterCmp(binaryNode, Condition.NE);
    }

    @Override
    public Node enterNE_STRICT(final BinaryNode binaryNode) {
        return enterCmp(binaryNode, Condition.NE);
    }

    @Override
    public Node enterOR(final BinaryNode binaryNode) {
        return enterAND_OR(binaryNode);
    }

    @Override
    public Node enterSAR(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.sar();
            }
        }.evaluate(binaryNode);

        return null;
    }

    @Override
    public Node enterSHL(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.shl();
            }
        }.evaluate(binaryNode);

        return null;
    }

    @Override
    public Node enterSHR(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.shr();
                method.convert(Type.LONG).load(0xffff_ffffL).and();
            }
        }.evaluate(binaryNode);

        return null;
    }

    @Override
    public Node enterSUB(final BinaryNode binaryNode) {
        new BinaryArith() {
            @Override
            protected void op() {
                method.sub();
            }
        }.evaluate(binaryNode);

        return null;
    }

    /*
     * Ternary visits.
     */
    @Override
    public Node enter(final TernaryNode ternaryNode) {
        if (ternaryNode.testResolved()) {
            return null;
        }

        final Node lhs   = ternaryNode.lhs();
        final Node rhs   = ternaryNode.rhs();
        final Node third = ternaryNode.third();

        final Symbol symbol     = ternaryNode.getSymbol();
        final Label  falseLabel = new Label("ternary_false");
        final Label  exitLabel  = new Label("ternary_exit");

        final Type widest = Type.widest(rhs.getType(), third.getType());

        load(lhs);
        assert lhs.getType().isBoolean() : "lhs in ternary must be boolean";

        // we still keep the conversion here as the AccessSpecializer can have separated the types, e.g. var y = x ? x=55 : 17
        // will left as (Object)x=55 : (Object)17 by Lower. Then the first term can be {I}x=55 of type int, which breaks the
        // symmetry for the temporary slot for this TernaryNode. This is evidence that we assign types and explicit conversions
        // to early, or Apply the AccessSpecializer too late. We are mostly probably looking for a separate type pass to
        // do this property. Then we never need any conversions in CodeGenerator
        method.ifeq(falseLabel);
        load(rhs);
        method.convert(widest);
        method._goto(exitLabel);
        method.label(falseLabel);
        load(third);
        method.convert(widest);
        method.label(exitLabel);
        method.store(symbol);

        return null;
    }

    /**
     * Generate all shared scope calls generated during codegen.
     */
    protected void generateScopeCalls() {
        for (final SharedScopeCall scopeAccess : scopeCalls.values()) {
            scopeAccess.generateScopeCall();
        }
    }

    /**
     * Get a shared static method representing a dynamic scope callsite.
     *
     * @param symbol the symbol
     * @param valueType the value type of the symbol
     * @param returnType the return type
     * @param paramTypes the parameter types
     * @param flags the callsite flags
     * @return an object representing a shared scope call
     */
    private SharedScopeCall getScopeCall(final Symbol symbol, final Type valueType, final Type returnType,
                                         final Type[] paramTypes, final int flags) {

        final SharedScopeCall scopeCall = new SharedScopeCall(symbol, valueType, returnType, paramTypes, flags);
        if (scopeCalls.containsKey(scopeCall)) {
            return scopeCalls.get(scopeCall);
        }
        scopeCall.setClassAndName(compileUnit, compiler);
        scopeCalls.put(scopeCall, scopeCall);
        return scopeCall;
    }

    /**
     * Get a shared static method representing a dynamic scope get access.
     *
     * @param type the type of the variable
     * @param symbol the symbol
     * @param flags the callsite flags
     * @return an object representing a shared scope call
     */
    private SharedScopeCall getScopeGet(final Type type, final Symbol symbol, final int flags) {

        final SharedScopeCall scopeCall = new SharedScopeCall(symbol, type, type, null, flags);
        if (scopeCalls.containsKey(scopeCall)) {
            return scopeCalls.get(scopeCall);
        }
        scopeCall.setClassAndName(compileUnit, compiler);
        scopeCalls.put(scopeCall, scopeCall);
        return scopeCall;
    }

    /**
     * Debug code used to print symbols
     *
     * @param block the block we are in
     * @param ident identifier for block or function where applicable
     */
    private void printSymbols(final Block block, final String ident) {
        if (!context._print_symbols) {
            return;
        }

        @SuppressWarnings("resource")
        final PrintWriter out = context.getErr();
        out.println("[BLOCK in '" + ident + "']");
        if (!block.printSymbols(out)) {
            out.println("<no symbols>");
        }
        out.println();
    }


    /**
     * The difference between a store and a self modifying store is that
     * the latter may load part of the target on the stack, e.g. the base
     * of an AccessNode or the base and index of an IndexNode. These are used
     * both as target and as an extra source. Previously it was problematic
     * for self modifying stores if the target/lhs didn't belong to one
     * of three trivial categories: IdentNode, AcessNodes, IndexNodes. In that
     * case it was evaluated and tagged as "resolved", which meant at the second
     * time the lhs of this store was read (e.g. in a = a (second) + b for a += b,
     * it would be evaluated to a nop in the scope and cause stack underflow
     *
     * see NASHORN-703
     *
     * @param <T>
     */
    private abstract class SelfModifyingStore<T extends Node> extends Store<T> {
        protected SelfModifyingStore(final T assignNode, final Node target) {
            super(assignNode, target);
        }

        @Override
        protected boolean isSelfModifying() {
            return true;
        }
    }

    /**
     * Helper class to generate stores
     */
    private abstract class Store<T extends Node> {

        /** An assignment node, e.g. x += y */
        protected final T assignNode;

        /** The target node to store to, e.g. x */
        private final Node target;

        /** Should the result always be discarded, no matter what? */
        private final boolean alwaysDiscard;

        /** How deep on the stack do the arguments go if this generates an indy call */
        private int depth;

        /** If we have too many arguments, we need temporary storage, this is stored in 'quick' */
        private Symbol quick;

        /**
         * Constructor
         *
         * @param assignNode the node representing the whole assignment
         * @param target     the target node of the assignment (destination)
         */
        protected Store(final T assignNode, final Node target) {
            this.assignNode = assignNode;
            this.target = target;
            this.alwaysDiscard = assignNode == target;
        }

        /**
         * Constructor
         *
         * @param assignNode the node representing the whole assignment
         */
        protected Store(final T assignNode) {
            this(assignNode, assignNode);
        }

        /**
         * Is this a self modifying store operation, e.g. *= or ++
         * @return true if self modifying store
         */
        protected boolean isSelfModifying() {
            return false;
        }

        private void prologue() {
            final Symbol targetSymbol = target.getSymbol();
            final Symbol scopeSymbol  = getCurrentFunctionNode().getScopeNode().getSymbol();

            /**
             * This loads the parts of the target, e.g base and index. they are kept
             * on the stack throughout the store and used at the end to execute it
             */

            target.accept(new NodeVisitor(compileUnit, method) {
                @Override
                public Node enter(final IdentNode node) {
                    if (targetSymbol.isScope()) {
                        method.load(scopeSymbol);
                        depth++;
                    }
                    return null;
                }

                private void enterBaseNode() {
                    assert target instanceof BaseNode : "error - base node " + target + " must be instanceof BaseNode";
                    final BaseNode baseNode = (BaseNode)target;
                    final Node     base     = baseNode.getBase();

                    load(base);
                    method.convert(Type.OBJECT);
                    depth += Type.OBJECT.getSlots();

                    if (isSelfModifying()) {
                        method.dup();
                    }
                }

                @Override
                public Node enter(final AccessNode node) {
                    enterBaseNode();
                    return null;
                }

                @Override
                public Node enter(final IndexNode node) {
                    enterBaseNode();

                    final Node index = node.getIndex();
                    // could be boolean here as well
                    load(index);
                    if (!index.getType().isNumeric()) {
                        method.convert(Type.OBJECT);
                    }
                    depth += index.getType().getSlots();

                    if (isSelfModifying()) {
                        //convert "base base index" to "base index base index"
                        method.dup(1);
                    }

                    return null;
                }

            });
        }

        private Symbol quickSymbol(final Type type) {
            return quickSymbol(type, QUICK_PREFIX.tag());
        }

        /**
         * Quick symbol generates an extra local variable, always using the same
         * slot, one that is available after the end of the frame.
         *
         * @param type the type of the symbol
         * @param prefix the prefix for the variable name for the symbol
         *
         * @return the quick symbol
         */
        private Symbol quickSymbol(final Type type, final String prefix) {
            final String name = compiler.uniqueName(prefix);
            final Symbol symbol = new Symbol(name, IS_TEMP | IS_INTERNAL, null, null);

            symbol.setType(type);
            symbol.setSlot(getCurrentBlock().getFrame().getSlotCount());

            return symbol;
        }

        // store the result that "lives on" after the op, e.g. "i" in i++ postfix.
        protected void storeNonDiscard() {
            if (assignNode.shouldDiscard() || alwaysDiscard) {
                assignNode.setDiscard(false);
                return;
            }

            final Symbol symbol = assignNode.getSymbol();
            if (symbol.hasSlot()) {
                method.dup().store(symbol);
                return;
            }

            if (method.dup(depth) == null) {
                method.dup();
                this.quick = quickSymbol(method.peekType());
                method.store(quick);
            }
        }

        private void epilogue() {
            final Symbol targetSymbol = target.getSymbol();
            final FunctionNode currentFunction = getCurrentFunctionNode();

            /**
             * Take the original target args from the stack and use them
             * together with the value to be stored to emit the store code
             */
            if (targetSymbol.hasSlot()) {
                method.convert(target.getType());
            }

            target.accept(new NodeVisitor(compileUnit, method) {
                @Override
                public Node enter(final IdentNode node) {
                    final Symbol symbol = target.getSymbol();
                    if (symbol.isScope()) {
                        if(symbol.isFastScope(currentFunction)) {
                            storeFastScopeVar(target.getType(), symbol, CALLSITE_SCOPE | getCallSiteFlags());
                        } else {
                            method.dynamicSet(target.getType(), node.getName(), CALLSITE_SCOPE | getCallSiteFlags());
                        }
                    } else {
                        assert targetSymbol != null;
                        method.store(targetSymbol);
                    }
                    return null;

                }

                @Override
                public Node enter(final AccessNode node) {
                    method.dynamicSet(node.getProperty().getType(), node.getProperty().getName(), getCallSiteFlags());
                    return null;
                }

                @Override
                public Node enter(final IndexNode node) {
                    method.dynamicSetIndex(getCallSiteFlags());
                    return null;
                }
            });


            // whatever is on the stack now is the final answer
        }

        protected abstract void evaluate();

        void store() {
            prologue();
            evaluate(); // leaves an operation of whatever the operationType was on the stack
            storeNonDiscard();
            epilogue();
            if (quick != null) {
                method.load(quick);
            }
        }

    }

    /*
     * Globals are special. We cannot refer to any Global (or NativeObject) class by .class, as they are different
     * for different contexts. As far as I can tell, the only NativeObject that we need to deal with like this
     * is from the code pipeline is Global
     */
    private MethodEmitter globalInstance() {
        return method.invokeStatic(Compiler.GLOBAL_OBJECT, "instance", "()L" + Compiler.GLOBAL_OBJECT + ';');
    }

    private MethodEmitter globalObjectPrototype() {
        return method.invokeStatic(Compiler.GLOBAL_OBJECT, "objectPrototype", methodDescriptor(ScriptObject.class));
    }

    private MethodEmitter globalAllocateArguments() {
        return method.invokeStatic(Compiler.GLOBAL_OBJECT, "allocateArguments", methodDescriptor(Object.class, Object[].class, Object.class, int.class));
    }

    private MethodEmitter globalNewRegExp() {
        return method.invokeStatic(Compiler.GLOBAL_OBJECT, "newRegExp", methodDescriptor(Object.class, String.class, String.class));
    }

    private MethodEmitter globalRegExpCopy() {
        return method.invokeStatic(Compiler.GLOBAL_OBJECT, "regExpCopy", methodDescriptor(Object.class, Object.class));
    }

    private MethodEmitter globalAllocateArray(final ArrayType type) {
        //make sure the native array is treated as an array type
        return method.invokeStatic(Compiler.GLOBAL_OBJECT, "allocate", methodDescriptor(Object.class, type.getTypeClass()), type);
    }

    private MethodEmitter globalIsEval() {
        return method.invokeStatic(Compiler.GLOBAL_OBJECT, "isEval", methodDescriptor(boolean.class, Object.class));
    }

    private MethodEmitter globalDirectEval() {
        return method.invokeStatic(Compiler.GLOBAL_OBJECT, "directEval",
                methodDescriptor(Object.class, Object.class, Object.class, Object.class, Object.class, Object.class));
    }
}
