/*
 * Copyright 2013 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.sbe.generation.java;

import uk.co.real_logic.sbe.generation.CodeGenerator;
import uk.co.real_logic.sbe.generation.OutputManager;
import uk.co.real_logic.sbe.ir.IntermediateRepresentation;
import uk.co.real_logic.sbe.ir.Signal;
import uk.co.real_logic.sbe.ir.Token;
import uk.co.real_logic.sbe.util.Verify;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import static uk.co.real_logic.sbe.generation.java.JavaUtil.*;

public class JavaGenerator implements CodeGenerator
{
    /** Class name to be used for visitor pattern that accesses the message header. */
    public static final String MESSAGE_HEADER_VISITOR = "MessageHeaderVisitor";

    private final IntermediateRepresentation ir;
    private final OutputManager outputManager;

    public JavaGenerator(final IntermediateRepresentation ir, final OutputManager outputManager)
        throws IOException
    {
        Verify.notNull(ir, "ir)");
        Verify.notNull(outputManager, "outputManager");

        this.ir = ir;
        this.outputManager = outputManager;
    }

    public void generateMessageHeaderStub() throws IOException
    {
        try (final Writer out = outputManager.createOutput(MESSAGE_HEADER_VISITOR))
        {
            generateFileHeader(out, ir.getPackageName());
            generateClassDeclaration(out, MESSAGE_HEADER_VISITOR);
            generateBufferConfig(out);

            final List<Token> tokens = ir.getHeader();
            generatePrimitiveEncodings(out, tokens.subList(1, tokens.size() - 1));

            out.append("}\n");
        }
    }

    public void generateTypeStubs() throws IOException
    {
        for (final List<Token> tokens : ir.types())
        {
            switch (tokens.get(0).signal())
            {
                case BEGIN_COMPOSITE:
                    break;

                case BEGIN_ENUM:
                    generateEnum(tokens);
                    break;

                case BEGIN_SET:
                    break;
            }
        }
    }

    public void generateMessageStubs() throws IOException
    {
        //To change body of implemented methods use File | Options | File Templates.
    }

    private void generateEnum(final List<Token> tokens) throws IOException
    {
        final String enumName = JavaUtil.toUpperFirstChar(tokens.get(0).name());

        try (final Writer out = outputManager.createOutput(enumName))
        {
            generateFileHeader(out, ir.getPackageName());
            generateEnumDeclaration(out, enumName);

            generateEnumValues(out, tokens.subList(1, tokens.size() - 1));
            generateEnumBody(out, tokens.get(0), enumName);

            generateEnumLookupMethod(out, tokens.subList(1, tokens.size() - 1), enumName);

            out.append("}\n");
        }
    }

    private void generateEnumValues(final Writer out, final List<Token> tokens) throws IOException
    {
        final StringBuilder sb = new StringBuilder();

        for (final Token token : tokens)
        {
            sb.append("    ").append(token.name()).append('(').append(token.options().constVal()).append("),\n");
        }

        sb.setLength(sb.length() - 2);
        sb.append(";\n\n");

        out.append(sb);
    }

    private void generateEnumBody(final Writer out, final Token token, final String enumName) throws IOException
    {
        final String javaEncodingType = javaTypeFor(token.primitiveType());

        out.append("    private final ").append(javaEncodingType).append(" value;\n\n")
           .append("    ").append(enumName).append("(final ").append(javaEncodingType).append(" value)\n")
           .append("    {\n")
           .append("        this.value = value;\n")
           .append("    }\n\n")
           .append("    public ").append(javaEncodingType).append(" value()\n")
           .append("    {\n")
           .append("        return value;\n")
           .append("    }\n\n");
    }

    private void generateEnumLookupMethod(final Writer out, final List<Token> tokens, final String enumName)
        throws IOException
    {
        final String javaEncodingType = javaTypeFor(tokens.get(0).primitiveType());

        out.append("    public ").append(enumName).append(" lookup(final ").append(javaEncodingType).append(" value)\n")
           .append("    {\n")
           .append("        switch (value)\n")
           .append("        {\n");

        for (final Token token : tokens)
        {
            final String constVal = token.options().constVal().toString();
            out.append("            case ").append(constVal).append(": return ").append(token.name()).append(";\n");
        }

        out.append("        }\n\n")
           .append("        throw new IllegalArgumentException(\"Unknown value: \" + value);\n")
           .append("    }\n");

    }

    private static void generateFileHeader(final Writer out, final String packageName)
        throws IOException
    {
        final String str = String.format(
            "/* Generated class message */\n" +
                "package %s;\n\n" +
                "import uk.co.real_logic.sbe.generation.java.*;\n\n",
            packageName
        );

        out.append(str);
    }

    private static void generateClassDeclaration(final Writer out, final String className)
        throws IOException
    {
        out.append("public class ").append(className).append("\n{\n");
    }


    private static void generateEnumDeclaration(final Writer out, final String name)
        throws IOException
    {
        out.append("public enum ").append(name).append("\n{\n");
    }

    private void generatePrimitiveEncodings(final Writer out, final List<Token> tokens)
        throws IOException
    {
        for (final Token token : tokens)
        {
            if (token.signal() == Signal.ENCODING)
            {
                final String typeName = javaTypeFor(token.primitiveType());
                final String methodPrefix = token.primitiveType().primitiveName();
                final String propertyName = token.name();
                final Integer offset = Integer.valueOf(token.offset());

                final String str = String.format(
                     "\n" +
                     "    public %s %s()\n" +
                     "    {\n" +
                     "        return JavaUtil.%sGet(buffer, offset + %d);\n" +
                     "    }\n\n" +
                     "    public void %s(final %s value)\n" +
                     "    {\n" +
                     "        JavaUtil.%sPut(buffer, offset + %d, value);\n" +
                     "    }\n",
                     typeName,
                     propertyName,
                     methodPrefix,
                     offset,
                     propertyName,
                     typeName,
                     methodPrefix,
                     offset
                );

                out.append(str);
            }
        }
    }

    private void generateBufferConfig(final Writer out) throws IOException
    {
        out.append("    private DirectBuffer buffer;\n")
           .append("    private int offset;\n\n")
           .append("    public void reset(final DirectBuffer buffer, final int offset)\n")
           .append("    {\n")
           .append("        this.buffer = buffer;\n")
           .append("        this.offset = offset;\n")
           .append("    }\n");
    }
}