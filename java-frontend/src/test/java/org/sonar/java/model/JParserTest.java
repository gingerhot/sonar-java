/*
 * SonarQube Java
 * Copyright (C) 2012-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.model;

import org.junit.ComparisonFailure;
import org.junit.Test;
import org.sonar.java.ast.parser.JavaParser;
import org.sonar.java.bytecode.loader.SquidClassLoader;
import org.sonar.java.resolve.SemanticModel;
import org.sonar.plugins.java.api.tree.BlockTree;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.CompilationUnitTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.VariableTree;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * TODO test shared identifiers in enum
 */
public class JParserTest {

  @org.junit.Ignore
  @Test
  public void invalid() {
    // TODO in CompareToResultTestCheckTest
    testExpression("(c++)++");
    // TODO in ForLoopIncrementSignCheckTest
    testExpression("(-i)++");
    // TODO in SystemExitCalledCheckTest
    testExpression("m()++");
  }

  @Test
  public void err() {
    try {
      // ASTNode.METHOD_DECLARATION with flag ASTNode.MALFORMED
      test("interface Foo { public foo(); // comment\n }");
      fail("exception expected");
    } catch (IndexOutOfBoundsException ignore) {
    }

    try {
      // TODO without check for syntax errors will cause IndexOutOfBoundsException
      JParser.parse("class C");
      fail("exception expected");
    } catch (UnsupportedOperationException e) {
      assertEquals("line 1: Syntax error, insert \"ClassBody\" to complete CompilationUnit", e.getMessage());
    }
  }

  @Test
  public void wip() {
    test("class C { void m(String... s) { m(new String[] {}); /* comment */ m(new String[] {}); } }");
    test("abstract class C { abstract int method(); }");
    test("class C { int f; }");
  }

  @org.junit.Ignore
  @Test
  public void eof() {
    CompilationUnitTree t = test("");
    assertEquals("", t.eofToken().text());
    assertEquals(1, t.eofToken().line());
    assertEquals(0, t.eofToken().column());
  }

  /**
   * @see org.eclipse.jdt.core.dom.InfixExpression#extendedOperands()
   */
  @Test
  public void extended_operands() {
    test("class C { void m() { m( 1 - 2 - 3 ); } }");

    // TODO no extendedOperands in case of parenthesises ?
    test("class C { void m() { m( (1 - 2) - 3 ); } }");
    test("class C { void m() { m( 1 - (2 - 3) ); } }");
  }

  /**
   * @see org.eclipse.jdt.core.dom.MethodDeclaration#extraDimensions()
   * @see org.eclipse.jdt.core.dom.VariableDeclarationFragment#extraDimensions()
   * @see org.eclipse.jdt.core.dom.SingleVariableDeclaration#extraDimensions()
   */
  @Test
  public void extra_dimensions() {
    test("interface I { int m()[]; }");
    test("interface I { int m(int p[]); }");
    test("interface I { int f1[], f2[][]; }");
  }

  /**
   * @see Tree.Kind#VAR_TYPE
   */
  @Test
  public void type_var() {
    test("class C { void m() { var i = 42; } }");
  }

  @Test
  public void empty_declarations() {
    // after each import declaration
    test("import i; ;");

    try {
      test("import a; ; import b;");
      fail("exception expected");
    } catch (UnsupportedOperationException e) {
      // TODO syntax tree is actually correct even in presence of syntax error
      assertEquals("line 1: Syntax error on token \";\", delete this token", e.getMessage());
    }

    // before first and after each body declaration
    test("class C { ; void m(); ; }");
  }

  /**
   * @see org.eclipse.jdt.core.dom.SingleVariableDeclaration#isVarargs()
   */
  @Test
  public void varargs() {
    test("class I { void m(int... p) { m(1); } }");
  }

  @Test
  public void declaration_package() {
    test("@Annotation package org.example;");
  }

  @Test
  public void declaration_class() {
    test("class A extends B implements I1, I2 { }");
  }

  @Test
  public void declaration_enum() {
    test("enum E { C1 , C2 }");
    test("enum E { C1 , C2 ; }");

    test("enum E { C() }");
    test("enum E { C { } }");
  }

  @Test
  public void statement_variable_declaration() {
    CompilationUnitTree t = test("class C { void m() { int a, b; } }");
    ClassTree c = (ClassTree) t.types().get(0);
    MethodTree m = (MethodTree) c.members().get(0);
    BlockTree s = m.block();
    assertNotNull(s);
    VariableTree s1 = (VariableTree) s.body().get(0);
    VariableTree s2 = (VariableTree) s.body().get(1);
    assertSame(s1.type(), s2.type());
  }

  @Test
  public void statement_for() {
    testStatement("for ( ; ; ) ;");
    testStatement("for (int i = 0, j = 0 ; ; i++, j++) ;");
  }

  @Test
  public void statement_switch() {
    test("class C { void m() { switch (0) { case 0: } } }");

    // Java 12 preview feature
    test("class C { void m() { switch (0) { case 0, 1: } } }");
    test("class C { void m() { switch (0) { case 0, 1 -> { break; } } } }");
  }

  @Test
  public void statement_try() {
    test("class C { void m() { try (R r1 = open(); r2;) { } } }");
  }

  @Test
  public void statement_constructor_invocation() {
    test("class C<T> { C() { <T>this(null); } C(Object o) { } }");
  }

  @Test
  public void expression_literal() {
    testExpression("-2147483648"); // Integer.MIN_VALUE
    testExpression("-9223372036854775808L"); // Long.MIN_VALUE
  }

  @Test
  public void expression_array_creation() {
    testExpression("new int[0]");
    testExpression("new int[0][1]");

    testExpression("new int[][] { { } , { } }");
  }

  @Test
  public void expression_type_method_reference() {
    testExpression("java.util.Map.Entry<String, String>::getClass");
  }

  @Test
  public void expression_super_method_reference() {
    testExpression("C.super::<T, T>m");
    testExpression("super::m");
  }

  @Test
  public void expression_creation_reference() {
    testExpression("C<T, T>::new");
  }

  @Test
  public void expression_super_method_invocation() {
    test("class C { class Inner { Inner() { C.super.toString(); } } }");
  }

  @Test
  public void expression_lambda() {
    testExpression("lambda( (p1, p2) -> {} )");
  }

  @Test
  public void expression_switch() {
    // Java 12 preview feature
    testExpression("switch (0) { case 0 -> 0; }");
  }

  @Test
  public void type_qualified() {
    testExpression("new a<b>. @Annotation c()");
  }

  @Test
  public void type_name_qualified() {
    testExpression("new a. @Annotation d()");
  }

  private void testStatement(String statement) {
    test("class C { void m() { " + statement + " } }");
  }

  private void testExpression(String expression) {
    test("class C { Object m() { return " + expression + " ; } }");
  }

  private static CompilationUnitTree test(String source) {
    TreeFormatter formatter = new TreeFormatter();
    formatter.showTokens = true;

    CompilationUnitTree oldTree = (CompilationUnitTree) JavaParser.createParser().parse(source);
    SemanticModel.createFor(oldTree, new SquidClassLoader(Collections.emptyList()));
    String expected = formatter.toString(oldTree);
    System.out.println(expected);

    CompilationUnitTree newTree = JParser.parse(source);
    String actual = formatter.toString(newTree);

    assertEquals(expected, actual);

    return newTree;
  }

}
