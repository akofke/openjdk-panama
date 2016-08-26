/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.util;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.api.DiagnosticFormatter;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.DefinedBy.Api;

import static com.sun.tools.javac.util.JCDiagnostic.DiagnosticType.*;

/** An abstraction of a diagnostic message generated by the compiler.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class JCDiagnostic implements Diagnostic<JavaFileObject> {
    /** A factory for creating diagnostic objects. */
    public static class Factory {
        /** The context key for the diagnostic factory. */
        protected static final Context.Key<JCDiagnostic.Factory> diagnosticFactoryKey = new Context.Key<>();

        /** Get the Factory instance for this context. */
        public static Factory instance(Context context) {
            Factory instance = context.get(diagnosticFactoryKey);
            if (instance == null)
                instance = new Factory(context);
            return instance;
        }

        DiagnosticFormatter<JCDiagnostic> formatter;
        final String prefix;
        final Set<DiagnosticFlag> defaultErrorFlags;

        /** Create a new diagnostic factory. */
        protected Factory(Context context) {
            this(JavacMessages.instance(context), "compiler");
            context.put(diagnosticFactoryKey, this);

            final Options options = Options.instance(context);
            initOptions(options);
            options.addListener(new Runnable() {
               public void run() {
                   initOptions(options);
               }
            });
        }

        private void initOptions(Options options) {
            if (options.isSet("onlySyntaxErrorsUnrecoverable"))
                defaultErrorFlags.add(DiagnosticFlag.RECOVERABLE);
        }

        /** Create a new diagnostic factory. */
        public Factory(JavacMessages messages, String prefix) {
            this.prefix = prefix;
            this.formatter = new BasicDiagnosticFormatter(messages);
            defaultErrorFlags = EnumSet.of(DiagnosticFlag.MANDATORY);
        }

        /**
         * Create an error diagnostic
         *  @param source The source of the compilation unit, if any, in which to report the error.
         *  @param pos    The source position at which to report the error.
         *  @param key    The key for the localized error message.
         *  @param args   Fields of the error message.
         */
        public JCDiagnostic error(
                DiagnosticFlag flag, DiagnosticSource source, DiagnosticPosition pos, String key, Object... args) {
            return error(flag, source, pos, errorKey(key, args));
        }

        /**
         * Create an error diagnostic
         *  @param source The source of the compilation unit, if any, in which to report the error.
         *  @param pos    The source position at which to report the error.
         *  @param errorKey    The key for the localized error message.
         */
        public JCDiagnostic error(
                DiagnosticFlag flag, DiagnosticSource source, DiagnosticPosition pos, Error errorKey) {
            JCDiagnostic diag = create(null, EnumSet.copyOf(defaultErrorFlags), source, pos, errorKey);
            if (flag != null) {
                diag.setFlag(flag);
            }
            return diag;
        }

        /**
         * Create a warning diagnostic that will not be hidden by the -nowarn or -Xlint:none options.
         *  @param lc     The lint category for the diagnostic
         *  @param source The source of the compilation unit, if any, in which to report the warning.
         *  @param pos    The source position at which to report the warning.
         *  @param key    The key for the localized warning message.
         *  @param args   Fields of the warning message.
         *  @see MandatoryWarningHandler
         */
        public JCDiagnostic mandatoryWarning(
                LintCategory lc,
                DiagnosticSource source, DiagnosticPosition pos, String key, Object... args) {
            return mandatoryWarning(lc, source, pos, warningKey(key, args));
        }

        /**
         * Create a warning diagnostic that will not be hidden by the -nowarn or -Xlint:none options.
         *  @param lc     The lint category for the diagnostic
         *  @param source The source of the compilation unit, if any, in which to report the warning.
         *  @param pos    The source position at which to report the warning.
         *  @param warningKey    The key for the localized warning message.
         *  @see MandatoryWarningHandler
         */
        public JCDiagnostic mandatoryWarning(
                LintCategory lc,
                DiagnosticSource source, DiagnosticPosition pos, Warning warningKey) {
            return create(lc, EnumSet.of(DiagnosticFlag.MANDATORY), source, pos, warningKey);
        }

        /**
         * Create a warning diagnostic.
         *  @param lc     The lint category for the diagnostic
         *  @param source The source of the compilation unit, if any, in which to report the warning.
         *  @param pos    The source position at which to report the warning.
         *  @param key    The key for the localized error message.
         *  @param args   Fields of the warning message.
         *  @see MandatoryWarningHandler
         */
        public JCDiagnostic warning(
                LintCategory lc, DiagnosticSource source, DiagnosticPosition pos, String key, Object... args) {
            return warning(lc, source, pos, warningKey(key, args));
        }

        /**
         * Create a warning diagnostic.
         *  @param lc     The lint category for the diagnostic
         *  @param source The source of the compilation unit, if any, in which to report the warning.
         *  @param pos    The source position at which to report the warning.
         *  @param warningKey    The key for the localized warning message.
         *  @see MandatoryWarningHandler
         */
        public JCDiagnostic warning(
                LintCategory lc, DiagnosticSource source, DiagnosticPosition pos, Warning warningKey) {
            return create(lc, EnumSet.noneOf(DiagnosticFlag.class), source, pos, warningKey);
        }

        /**
         * Create a note diagnostic that will not be hidden by the -nowarn or -Xlint:none options.
         *  @param source The source of the compilation unit, if any, in which to report the warning.
         *  @param key    The key for the localized warning message.
         *  @param args   Fields of the warning message.
         *  @see MandatoryWarningHandler
         */
        public JCDiagnostic mandatoryNote(DiagnosticSource source, String key, Object... args) {
            return mandatoryNote(source, noteKey(key, args));
        }

        /**
         * Create a note diagnostic that will not be hidden by the -nowarn or -Xlint:none options.
         *  @param noteKey    The key for the localized note message.
         *  @see MandatoryWarningHandler
         */
        public JCDiagnostic mandatoryNote(DiagnosticSource source, Note noteKey) {
            return create(null, EnumSet.of(DiagnosticFlag.MANDATORY), source, null, noteKey);
        }

        /**
         * Create a note diagnostic.
         *  @param key    The key for the localized error message.
         *  @param args   Fields of the message.
         */
        public JCDiagnostic note(
                DiagnosticSource source, DiagnosticPosition pos, String key, Object... args) {
            return note(source, pos, noteKey(key, args));
        }

        /**
         * Create a note diagnostic.
         *  @param source The source of the compilation unit, if any, in which to report the note.
         *  @param pos    The source position at which to report the note.
         *  @param noteKey    The key for the localized note message.
         */
        public JCDiagnostic note(
                DiagnosticSource source, DiagnosticPosition pos, Note noteKey) {
            return create(null, EnumSet.noneOf(DiagnosticFlag.class), source, pos, noteKey);
        }

        /**
         * Create a fragment diagnostic, for use as an argument in other diagnostics
         *  @param key    The key for the localized message.
         *  @param args   Fields of the message.
         */
        public JCDiagnostic fragment(String key, Object... args) {
            return fragment(fragmentKey(key, args));
        }

        /**
         * Create a fragment diagnostic, for use as an argument in other diagnostics
         *  @param fragmentKey    The key for the localized subdiagnostic message.
         */
        public JCDiagnostic fragment(Fragment fragmentKey) {
            return create(null, EnumSet.noneOf(DiagnosticFlag.class), null, null, fragmentKey);
        }

        /**
         * Create a new diagnostic of the given kind, which is not mandatory and which has
         * no lint category.
         *  @param kind        The diagnostic kind
         *  @param source      The source of the compilation unit, if any, in which to report the message.
         *  @param pos         The source position at which to report the message.
         *  @param key         The key for the localized message.
         *  @param args        Fields of the message.
         */
        public JCDiagnostic create(
                DiagnosticType kind, DiagnosticSource source, DiagnosticPosition pos, String key, Object... args) {
            return create(null, EnumSet.noneOf(DiagnosticFlag.class), source, pos, DiagnosticInfo.of(kind, prefix, key, args));
        }

        /**
         * Create a new diagnostic of the given kind, which is not mandatory and which has
         * no lint category.
         *  @param source      The source of the compilation unit, if any, in which to report the message.
         *  @param pos         The source position at which to report the message.
         *  @param diagnosticInfo         The key for the localized message.
         */
        public JCDiagnostic create(
                DiagnosticSource source, DiagnosticPosition pos, DiagnosticInfo diagnosticInfo) {
            return create(null, EnumSet.noneOf(DiagnosticFlag.class), source, pos, diagnosticInfo);
        }

        /**
         * Create a new diagnostic of the given kind.
         *  @param kind        The diagnostic kind
         *  @param lc          The lint category, if applicable, or null
         *  @param flags       The set of flags for the diagnostic
         *  @param source      The source of the compilation unit, if any, in which to report the message.
         *  @param pos         The source position at which to report the message.
         *  @param key         The key for the localized message.
         *  @param args        Fields of the message.
         */
        public JCDiagnostic create(DiagnosticType kind,
                LintCategory lc, Set<DiagnosticFlag> flags, DiagnosticSource source, DiagnosticPosition pos, String key, Object... args) {
            return create(lc, flags, source, pos, DiagnosticInfo.of(kind, prefix, key, args));
        }

        /**
         * Create a new diagnostic with given key.
         *  @param lc          The lint category, if applicable, or null
         *  @param flags       The set of flags for the diagnostic
         *  @param source      The source of the compilation unit, if any, in which to report the message.
         *  @param pos         The source position at which to report the message.
         *  @param diagnosticInfo    The key for the localized message.
         */
        public JCDiagnostic create(
                LintCategory lc, Set<DiagnosticFlag> flags, DiagnosticSource source, DiagnosticPosition pos, DiagnosticInfo diagnosticInfo) {
            return new JCDiagnostic(formatter, normalize(diagnosticInfo), lc, flags, source, pos);
        }
        //where
            DiagnosticInfo normalize(DiagnosticInfo diagnosticInfo) {
                //replace all nested FragmentKey with full-blown JCDiagnostic objects
                return DiagnosticInfo.of(diagnosticInfo.type, diagnosticInfo.prefix, diagnosticInfo.code,
                        Stream.of(diagnosticInfo.args).map(o -> {
                            return (o instanceof Fragment) ?
                                    fragment((Fragment)o) : o;
                        }).toArray());
            }

        /**
         * Create a new error key.
         */
        Error errorKey(String code, Object... args) {
            return (Error)DiagnosticInfo.of(ERROR, prefix, code, args);
        }

        /**
         * Create a new warning key.
         */
        Warning warningKey(String code, Object... args) {
            return (Warning)DiagnosticInfo.of(WARNING, prefix, code, args);
        }

        /**
         * Create a new note key.
         */
        Note noteKey(String code, Object... args) {
            return (Note)DiagnosticInfo.of(NOTE, prefix, code, args);
        }

        /**
         * Create a new fragment key.
         */
        Fragment fragmentKey(String code, Object... args) {
            return (Fragment)DiagnosticInfo.of(FRAGMENT, prefix, code, args);
        }
    }



    /**
     * Create a fragment diagnostic, for use as an argument in other diagnostics
     *  @param key    The key for the localized error message.
     *  @param args   Fields of the error message.
     *
     */
    @Deprecated
    public static JCDiagnostic fragment(String key, Object... args) {
        return new JCDiagnostic(getFragmentFormatter(),
                              DiagnosticInfo.of(FRAGMENT,
                                      "compiler",
                                      key,
                                      args),
                              null,
                              EnumSet.noneOf(DiagnosticFlag.class),
                              null,
                              null);
    }
    //where
    @Deprecated
    public static DiagnosticFormatter<JCDiagnostic> getFragmentFormatter() {
        if (fragmentFormatter == null) {
            fragmentFormatter = new BasicDiagnosticFormatter(JavacMessages.getDefaultMessages());
        }
        return fragmentFormatter;
    }

    /**
     * A DiagnosticType defines the type of the diagnostic.
     **/
    public enum DiagnosticType {
        /** A fragment of an enclosing diagnostic. */
        FRAGMENT("misc"),
        /** A note: similar to, but less serious than, a warning. */
        NOTE("note"),
        /** A warning. */
        WARNING("warn"),
        /** An error. */
        ERROR("err");

        final String key;

        /** Create a DiagnosticType.
         * @param key A string used to create the resource key for the diagnostic.
         */
        DiagnosticType(String key) {
            this.key = key;
        }
    }

    /**
     * A DiagnosticPosition provides information about the positions in a file
     * that gave rise to a diagnostic. It always defines a "preferred" position
     * that most accurately defines the location of the diagnostic, it may also
     * provide a related tree node that spans that location.
     */
    public static interface DiagnosticPosition {
        /** Gets the tree node, if any, to which the diagnostic applies. */
        JCTree getTree();
        /** If there is a tree node, get the start position of the tree node.
         *  Otherwise, just returns the same as getPreferredPosition(). */
        int getStartPosition();
        /** Get the position within the file that most accurately defines the
         *  location for the diagnostic. */
        int getPreferredPosition();
        /** If there is a tree node, and if endPositions are available, get
         *  the end position of the tree node. Otherwise, just returns the
         *  same as getPreferredPosition(). */
        int getEndPosition(EndPosTable endPosTable);
    }

    /**
     * A DiagnosticPosition that simply identifies a position, but no related
     * tree node, as the location for a diagnostic. Used for scanner and parser
     * diagnostics. */
    public static class SimpleDiagnosticPosition implements DiagnosticPosition {
        public SimpleDiagnosticPosition(int pos) {
            this.pos = pos;
        }

        public JCTree getTree() {
            return null;
        }

        public int getStartPosition() {
            return pos;
        }

        public int getPreferredPosition() {
            return pos;
        }

        public int getEndPosition(EndPosTable endPosTable) {
            return pos;
        }

        private final int pos;
    }

    public enum DiagnosticFlag {
        MANDATORY,
        RESOLVE_ERROR,
        SYNTAX,
        RECOVERABLE,
        NON_DEFERRABLE,
        COMPRESSED,
        /** Print multiple errors for same source locations.
         */
        MULTIPLE,
        /** Flag for not-supported-in-source-X errors.
         */
        SOURCE_LEVEL;
    }

    private final DiagnosticSource source;
    private final DiagnosticPosition position;
    private final DiagnosticInfo diagnosticInfo;
    private final Set<DiagnosticFlag> flags;
    private final LintCategory lintCategory;

    /** source line position (set lazily) */
    private SourcePosition sourcePosition;

    /**
     * This class is used to defer the line/column position fetch logic after diagnostic construction.
     */
    class SourcePosition {

        private final int line;
        private final int column;

        SourcePosition() {
            int n = (position == null ? Position.NOPOS : position.getPreferredPosition());
            if (n == Position.NOPOS || source == null)
                line = column = -1;
            else {
                line = source.getLineNumber(n);
                column = source.getColumnNumber(n, true);
            }
        }

        public int getLineNumber() {
            return line;
        }

        public int getColumnNumber() {
            return column;
        }
    }

    /**
     * A diagnostic key object encapsulates basic properties of a diagnostic, such as the resource key,
     * the arguments and the kind associated with the diagnostic object. Diagnostic keys can be either
     * created programmatically (by using the supplied factory method) or obtained through build-time
     * generated factory methods.
     */
    public static abstract class DiagnosticInfo {

        /** The diagnostic kind (i.e. error). */
        DiagnosticType type;

        /** The diagnostic prefix (i.e. 'javac'); used to compute full resource key. */
        String prefix;

        /** The diagnostic code (i.e. 'cannot.resolve.sym'); together with {@code prefix} it forms
         * the full resource key. */
        String code;

        /** The diagnostic arguments. */
        Object[] args;

        private DiagnosticInfo(DiagnosticType type, String prefix, String code, Object... args) {
            this.type = type;
            this.prefix = prefix;
            this.code = code;
            this.args = args;
        }

        /**
         * Compute the resource key.
         */
        public String key() {
            return prefix + "." + type.key + "." + code;
        }

        /**
         * Static factory method; build a custom diagnostic key using given kind, prefix, code and args.
         */
        public static DiagnosticInfo of(DiagnosticType type, String prefix, String code, Object... args) {
            switch (type) {
                case ERROR:
                    return new Error(prefix, code, args);
                case WARNING:
                    return new Warning(prefix, code, args);
                case NOTE:
                    return new Note(prefix, code, args);
                case FRAGMENT:
                    return new Fragment(prefix, code, args);
                default:
                    Assert.error("Wrong diagnostic type: " + type);
                    return null;
            }
        }

    }

    /**
     * Class representing error diagnostic keys.
     */
    public static final class Error extends DiagnosticInfo {
        public Error(String prefix, String key, Object... args) {
            super(DiagnosticType.ERROR, prefix, key, args);
        }
    }

    /**
     * Class representing warning diagnostic keys.
     */
    public static final class Warning extends DiagnosticInfo {
        public Warning(String prefix, String key, Object... args) {
            super(DiagnosticType.WARNING, prefix, key, args);
        }
    }

    /**
     * Class representing note diagnostic keys.
     */
    public static final class Note extends DiagnosticInfo {
        public Note(String prefix, String key, Object... args) {
            super(DiagnosticType.NOTE, prefix, key, args);
        }
    }

    /**
     * Class representing fragment diagnostic keys.
     */
    public static final class Fragment extends DiagnosticInfo {
        public Fragment(String prefix, String key, Object... args) {
            super(DiagnosticType.FRAGMENT, prefix, key, args);
        }
    }

    /**
     * Create a diagnostic object.
     * @param formatter the formatter to use for the diagnostic
     * @param diagnosticInfo the diagnostic key
     * @param lc     the lint category for the diagnostic
     * @param source the name of the source file, or null if none.
     * @param pos the character offset within the source file, if given.
     */
    protected JCDiagnostic(DiagnosticFormatter<JCDiagnostic> formatter,
                       DiagnosticInfo diagnosticInfo,
                       LintCategory lc,
                       Set<DiagnosticFlag> flags,
                       DiagnosticSource source,
                       DiagnosticPosition pos) {
        if (source == null && pos != null && pos.getPreferredPosition() != Position.NOPOS)
            throw new IllegalArgumentException();

        this.defaultFormatter = formatter;
        this.diagnosticInfo = diagnosticInfo;
        this.lintCategory = lc;
        this.flags = flags;
        this.source = source;
        this.position = pos;
    }

    /**
     * Get the type of this diagnostic.
     * @return the type of this diagnostic
     */
    public DiagnosticType getType() {
        return diagnosticInfo.type;
    }

    /**
     * Get the subdiagnostic list
     * @return subdiagnostic list
     */
    public List<JCDiagnostic> getSubdiagnostics() {
        return List.nil();
    }

    public boolean isMultiline() {
        return false;
    }

    /**
     * Check whether or not this diagnostic is required to be shown.
     * @return true if this diagnostic is required to be shown.
     */
    public boolean isMandatory() {
        return flags.contains(DiagnosticFlag.MANDATORY);
    }

    /**
     * Check whether this diagnostic has an associated lint category.
     */
    public boolean hasLintCategory() {
        return (lintCategory != null);
    }

    /**
     * Get the associated lint category, or null if none.
     */
    public LintCategory getLintCategory() {
        return lintCategory;
    }

    /**
     * Get the name of the source file referred to by this diagnostic.
     * @return the name of the source referred to with this diagnostic, or null if none
     */
    @DefinedBy(Api.COMPILER)
    public JavaFileObject getSource() {
        if (source == null)
            return null;
        else
            return source.getFile();
    }

    /**
     * Get the source referred to by this diagnostic.
     * @return the source referred to with this diagnostic, or null if none
     */
    public DiagnosticSource getDiagnosticSource() {
        return source;
    }

    protected int getIntStartPosition() {
        return (position == null ? Position.NOPOS : position.getStartPosition());
    }

    protected int getIntPosition() {
        return (position == null ? Position.NOPOS : position.getPreferredPosition());
    }

    protected int getIntEndPosition() {
        return (position == null ? Position.NOPOS : position.getEndPosition(source.getEndPosTable()));
    }

    @DefinedBy(Api.COMPILER)
    public long getStartPosition() {
        return getIntStartPosition();
    }

    @DefinedBy(Api.COMPILER)
    public long getPosition() {
        return getIntPosition();
    }

    @DefinedBy(Api.COMPILER)
    public long getEndPosition() {
        return getIntEndPosition();
    }

    public DiagnosticPosition getDiagnosticPosition() {
        return position;
    }

    /**
     * Get the line number within the source referred to by this diagnostic.
     * @return  the line number within the source referred to by this diagnostic
     */
    @DefinedBy(Api.COMPILER)
    public long getLineNumber() {
        if (sourcePosition == null) {
            sourcePosition = new SourcePosition();
        }
        return sourcePosition.getLineNumber();
    }

    /**
     * Get the column number within the line of source referred to by this diagnostic.
     * @return  the column number within the line of source referred to by this diagnostic
     */
    @DefinedBy(Api.COMPILER)
    public long getColumnNumber() {
        if (sourcePosition == null) {
            sourcePosition = new SourcePosition();
        }
        return sourcePosition.getColumnNumber();
    }

    /**
     * Get the arguments to be included in the text of the diagnostic.
     * @return  the arguments to be included in the text of the diagnostic
     */
    public Object[] getArgs() {
        return diagnosticInfo.args;
    }

    /**
     * Get the prefix string associated with this type of diagnostic.
     * @return the prefix string associated with this type of diagnostic
     */
    public String getPrefix() {
        return getPrefix(diagnosticInfo.type);
    }

    /**
     * Get the prefix string associated with a particular type of diagnostic.
     * @return the prefix string associated with a particular type of diagnostic
     */
    public String getPrefix(DiagnosticType dt) {
        return defaultFormatter.formatKind(this, Locale.getDefault());
    }

    /**
     * Return the standard presentation of this diagnostic.
     */
    @Override
    public String toString() {
        return defaultFormatter.format(this, Locale.getDefault());
    }

    private DiagnosticFormatter<JCDiagnostic> defaultFormatter;
    @Deprecated
    private static DiagnosticFormatter<JCDiagnostic> fragmentFormatter;

    // Methods for javax.tools.Diagnostic

    @DefinedBy(Api.COMPILER)
    public Diagnostic.Kind getKind() {
        switch (diagnosticInfo.type) {
        case NOTE:
            return Diagnostic.Kind.NOTE;
        case WARNING:
            return flags.contains(DiagnosticFlag.MANDATORY)
                    ? Diagnostic.Kind.MANDATORY_WARNING
                    : Diagnostic.Kind.WARNING;
        case ERROR:
            return Diagnostic.Kind.ERROR;
        default:
            return Diagnostic.Kind.OTHER;
        }
    }

    @DefinedBy(Api.COMPILER)
    public String getCode() {
        return diagnosticInfo.key();
    }

    @DefinedBy(Api.COMPILER)
    public String getMessage(Locale locale) {
        return defaultFormatter.formatMessage(this, locale);
    }

    public void setFlag(DiagnosticFlag flag) {
        flags.add(flag);

        if (diagnosticInfo.type == DiagnosticType.ERROR) {
            switch (flag) {
                case SYNTAX:
                    flags.remove(DiagnosticFlag.RECOVERABLE);
                    break;
                case RESOLVE_ERROR:
                    flags.add(DiagnosticFlag.RECOVERABLE);
                    break;
            }
        }
    }

    public boolean isFlagSet(DiagnosticFlag flag) {
        return flags.contains(flag);
    }

    public static class MultilineDiagnostic extends JCDiagnostic {

        private final List<JCDiagnostic> subdiagnostics;

        public MultilineDiagnostic(JCDiagnostic other, List<JCDiagnostic> subdiagnostics) {
            super(other.defaultFormatter,
                  other.diagnosticInfo,
                  other.getLintCategory(),
                  other.flags,
                  other.getDiagnosticSource(),
                  other.position);
            this.subdiagnostics = subdiagnostics;
        }

        @Override
        public List<JCDiagnostic> getSubdiagnostics() {
            return subdiagnostics;
        }

        @Override
        public boolean isMultiline() {
            return true;
        }
    }
}
