/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package net.sourceforge.pmd.lang.java.rule.imports;

import java.util.ArrayList;
import java.util.List;

import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.lang.java.ast.ASTCompilationUnit;
import net.sourceforge.pmd.lang.java.ast.ASTImportDeclaration;
import net.sourceforge.pmd.lang.java.ast.ASTName;
import net.sourceforge.pmd.lang.java.ast.ASTPackageDeclaration;
import net.sourceforge.pmd.lang.java.ast.JavaNode;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;

public class UnnecessaryFullyQualifiedNameRule extends AbstractJavaRule {

    private List<ASTImportDeclaration> imports = new ArrayList<ASTImportDeclaration>();
    private List<ASTImportDeclaration> matches = new ArrayList<ASTImportDeclaration>();

    public UnnecessaryFullyQualifiedNameRule() {
	super.addRuleChainVisit(ASTCompilationUnit.class);
	super.addRuleChainVisit(ASTImportDeclaration.class);
	super.addRuleChainVisit(ASTClassOrInterfaceType.class);
	super.addRuleChainVisit(ASTName.class);
    }

    @Override
    public Object visit(ASTCompilationUnit node, Object data) {
	imports.clear();
	return data;
    }

    @Override
    public Object visit(ASTImportDeclaration node, Object data) {
	imports.add(node);
	return data;
    }

    @Override
    public Object visit(ASTClassOrInterfaceType node, Object data) {
	checkImports(node, data, false);
	return data;
    }

    @Override
    public Object visit(ASTName node, Object data) {
	if (!(node.jjtGetParent() instanceof ASTImportDeclaration)
	        && !(node.jjtGetParent() instanceof ASTPackageDeclaration)) {
	    checkImports(node, data, true);
	}
	return data;
    }

    private void checkImports(JavaNode node, Object data, boolean checkStatic) {
	String name = node.getImage();
	matches.clear();

	//  Find all "matching" import declarations
	for (ASTImportDeclaration importDeclaration : imports) {
	    if (importDeclaration.isImportOnDemand()) {
		// On demand import exactly matches the package of the type
		if (name.startsWith(importDeclaration.getImportedName())) {
		    if (name.lastIndexOf('.') == importDeclaration.getImportedName().length()) {
			matches.add(importDeclaration);
			continue;
		    }
		}
	    } else {
		// Exact match of imported class
		if (name.equals(importDeclaration.getImportedName())) {
		    matches.add(importDeclaration);
		    continue;
		}
		// Match of static method call on imported class
		if (name.startsWith(importDeclaration.getImportedName())) {
		    if (name.lastIndexOf('.') == importDeclaration.getImportedName().length()) {
			matches.add(importDeclaration);
			continue;
		    }
		}
	    }
	}

	// If there is no direct match, consider if we match the tail end of a
	// direct static import, but also a static method on a class import?
	// For example:
	//
	//    import java.util.Arrays;
	//    import static java.util.Arrays.asList;
	//    static {
	//       List list1 = Arrays.asList("foo");  // Array class name not needed!
	//       List list2 = asList("foo"); // Preferred, used static import
	//    }
	if (matches.isEmpty() && name.indexOf('.') >= 0) {
	    for (ASTImportDeclaration importDeclaration : imports) {
		if (importDeclaration.isStatic()) {
		    String[] importParts = importDeclaration.getImportedName().split("\\.");
		    String[] nameParts = name.split("\\.");
		    if (importDeclaration.isImportOnDemand()) {
			//  Name class part matches class part of static import?
			if (nameParts[nameParts.length - 2].equals(importParts[importParts.length - 1])) {
			    matches.add(importDeclaration);
			}
		    } else {
			// Last 2 parts match?
			if (nameParts[nameParts.length - 1].equals(importParts[importParts.length - 1])
				&& nameParts[nameParts.length - 2].equals(importParts[importParts.length - 2])) {
			    matches.add(importDeclaration);
			}
		    }
		}
	    }
	}

	if (!matches.isEmpty()) {
	    ASTImportDeclaration firstMatch = matches.get(0);
        String importStr = firstMatch.getImportedName() + (matches.get(0).isImportOnDemand() ? ".*" : "");
	    String type = firstMatch.isStatic() ? "static " : "";
	    addViolation(data, node, new Object[] { node.getImage(), importStr, type });
	}

	matches.clear();
    }
}
