package online.devtools.eclipse.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.PrimitiveType.Code;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.ui.CodeGeneration;
import org.eclipse.text.edits.TextEdit;

import online.devtools.eclipse.handlers.tools.CodeSettings;
import online.devtools.eclipse.handlers.tools.GenerationTools;

public class CodeGenerationOperation {

	private static final String BUILDER_TYPE_NAME = "Builder";

	private final IField[] fields;

	private final CodeSettings codeGenerationSettings;

	public CodeGenerationOperation(final IField[] fields, final CodeSettings codeGenerationSettings) {
		this.fields = fields;
		this.codeGenerationSettings = codeGenerationSettings;
	}

	public void run() {
		final ITypeRoot typeRoot = fields[0].getTypeRoot();
		final IType type = typeRoot.findPrimaryType();
		final ASTParser parser = ASTParser.newParser(AST.JLS8);

		parser.setSource(typeRoot);

		final CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
		final ASTRewrite astRewrite = ASTRewrite.create(compilationUnit.getAST());
		final AST ast = astRewrite.getAST();

		try {
			final AbstractTypeDeclaration parent = getParent(compilationUnit, type, AbstractTypeDeclaration.class);
			final ListRewrite listRewrite = astRewrite.getListRewrite(parent, parent.getBodyDeclarationsProperty());

			MethodDeclaration insertionPoint = null;

			if (codeGenerationSettings.generateBuilder) {
				insertionPoint = addConstructor(listRewrite, astRewrite, type, ast.newModifiers(Modifier.PRIVATE));
				addBuilderAccessor(listRewrite, astRewrite);
				addBuilder(listRewrite, astRewrite, insertionPoint, type);
			} else {
				insertionPoint = addConstructor(listRewrite, astRewrite, type, ast.newModifiers(Modifier.PUBLIC));
			}

			// TODO: For both cases remove setters if exist
			if (codeGenerationSettings.generateFinalFields) {
				addPublicFinalFields(listRewrite, ast);
			} else {
				addGetters(listRewrite, astRewrite, compilationUnit, type, insertionPoint);
			}

			save(type.getCompilationUnit(), astRewrite);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	private void addBuilder(final ListRewrite listRewrite, final ASTRewrite astRewrite,
			final MethodDeclaration constructorToInvoke, final IType parentClass) throws JavaModelException {
		final AST ast = astRewrite.getAST();
		final TypeDeclaration builderType = ast.newTypeDeclaration();

		builderType.modifiers().add(createPublicModifier(ast));
		builderType.modifiers().add(createStaticModifier(ast));
		builderType.setName(ast.newSimpleName(BUILDER_TYPE_NAME));

		final LinkedList<FieldDeclaration> fieldsDeclaration = new LinkedList<>();
		final LinkedList<MethodDeclaration> fieldAssignments = new LinkedList<>();

		for (IField field : fields) {
			fieldsDeclaration.add(createField(ast, field, ast.newModifiers(Modifier.PRIVATE)));
			fieldAssignments.add(createBuilderFieldAssignment(ast, field));
		}

		builderType.bodyDeclarations().addAll(fieldsDeclaration);
		builderType.bodyDeclarations().addAll(fieldAssignments);
		builderType.bodyDeclarations().add(createBuildMethod(ast, constructorToInvoke, parentClass));
		listRewrite.insertLast(builderType, null);
	}

	private void addBuilderAccessor(final ListRewrite listRewrite, final ASTRewrite astRewrite)
			throws JavaModelException {
		listRewrite.insertLast(createBuilderAccessor(astRewrite.getAST()), null);
	}

	/**
	 * 
	 * Adds a constructor.
	 * 
	 * 
	 * If a constructor already exists and should not be replaced then this
	 * method returns the existing constructor
	 * 
	 * 
	 * @param listRewrite
	 * @param astRewrite
	 * @param type
	 * @param modifiers
	 *            Modifiers for the constructor
	 * 
	 * @return The ASTNode for the constructor. Used as an insertion point for
	 *         the next statement in most cases. In case the constructor is not
	 *         added (if it exists and should not be replaced) the insertion
	 *         point is the existing constructor otherwise it is the newly added
	 *         constructor
	 * 
	 * @throws CoreException
	 */
	private MethodDeclaration addConstructor(final ListRewrite listRewrite, final ASTRewrite astRewrite,
			final IType type, final Collection<IExtendedModifier> modifiers) throws CoreException {
		return addConstructor(listRewrite, astRewrite, type, modifiers,
				existingConstructor -> existingConstructor != null
						&& !codeGenerationSettings.replaceExistingDeclarations);
	}

	private MethodDeclaration addConstructor(final ListRewrite listRewrite, final ASTRewrite astRewrite,
			final IType type, final Collection<IExtendedModifier> modifiers,
			Predicate<IMethod> shouldReplaceExistingConstructor) throws CoreException {
		final String constructorName = type.getTypeQualifiedName('.');
		final IMethod existingConstructor = findMethod(constructorName, type, getFieldsTypes(), true);

		if (shouldReplaceExistingConstructor != null && shouldReplaceExistingConstructor.test(existingConstructor)) {
			return (MethodDeclaration) getNode(listRewrite, existingConstructor.getSourceRange());
		}

		removeMethod(existingConstructor, listRewrite);
		ASTNode insertionPoint = getLastField(listRewrite);
		final MethodDeclaration constructor = createConstructor(constructorName, fields, listRewrite, astRewrite,
				modifiers);
		if (codeGenerationSettings.addComments) {
			final String comment = CodeGeneration.getMethodComment(type.getCompilationUnit(), type.getElementName(),
					constructor, null, getLineDelimiter());
			insertionPoint = insertComment(comment, listRewrite, astRewrite, insertionPoint);
		}

		listRewrite.insertAfter(constructor, insertionPoint, null);
		return constructor;
	}

	private void addGetters(final ListRewrite listRewrite, final ASTRewrite astRewrite,
			final CompilationUnit compilationUnit, final IType type, ASTNode insertPoint) throws CoreException {
		final AST ast = astRewrite.getAST();
		for (IField field : fields) {
			final String methodName = getGetterName(field);
			final IMethod existingMethod = findMethod(methodName, type, new String[0], false);

			if (existingMethod == null
					|| (existingMethod != null && codeGenerationSettings.replaceExistingDeclarations)) {
				removeMethod(existingMethod, listRewrite);
				if (codeGenerationSettings.addComments) {
					final String fieldName = field.getElementName();
					final String comment = CodeGeneration.getGetterComment(type.getCompilationUnit(),
							type.getElementName(), methodName, fieldName, getType(field), fieldName,
							getLineDelimiter());
					insertPoint = insertComment(comment, listRewrite, astRewrite, insertPoint);
				}

				final MethodDeclaration createGetterMethod = createGetterMethod(ast, field, methodName);
				listRewrite.insertAfter(createGetterMethod, insertPoint, null);
				addFinalFieldModifier(astRewrite, compilationUnit, field);
				insertPoint = createGetterMethod;
			}
		}
	}

	private ASTNode insertComment(final String comment, final ListRewrite listRewrite, final ASTRewrite astRewrite,
			final ASTNode insertionPoint) {
		if (comment != null) {
			final ASTNode commentPlaceholder = astRewrite.createStringPlaceholder(comment, ASTNode.BLOCK_COMMENT);
			listRewrite.insertAfter(commentPlaceholder, insertionPoint, null);
			return commentPlaceholder;
		}
		return insertionPoint;
	}

	private String getLineDelimiter() {
		return System.getProperty("line.separator", "\n");
	}

	private String getGetterName(IField field) throws JavaModelException {
		return GenerationTools.getGetterName(field, codeGenerationSettings.useIsForBooleanGetters);
	}

	private void addPublicFinalFields(final ListRewrite listRewrite, final AST ast) throws JavaModelException {
		for (IField field : fields) {
			listRewrite.replace(getNode(listRewrite, field), createPublicFinalField(ast, field), null);
		}
	}

	private ASTNode getLastField(final ListRewrite listRewrite) throws JavaModelException {
		return getNode(listRewrite, fields[fields.length - 1]);
	}

	private void save(ICompilationUnit cu, ASTRewrite astRewrite) throws CoreException {
		TextEdit edit = astRewrite.rewriteAST();
		cu.applyTextEdit(edit, null);
		cu.save(null, true);
	}

	private ASTNode getNode(final ListRewrite listRewrite, final IField field) throws JavaModelException {
		return getNode(listRewrite, field.getSourceRange());
	}

	private ASTNode getNode(final ListRewrite listRewrite, final ISourceRange sourceRange) {
		if (sourceRange == null) {
			return null;
		}

		int insertPos = sourceRange.getOffset();
		final List<? extends ASTNode> members = listRewrite.getOriginalList();
		for (ASTNode node : members) {
			if (node.getStartPosition() >= insertPos) {
				return node;
			}
		}
		return null;
	}

	private ASTNode getParent(ASTNode node, Class<? extends ASTNode> parentClass) {
		do {
			node = node.getParent();
		} while (node != null && !parentClass.isInstance(node));

		return node;
	}

	private <T extends ASTNode> T getParent(CompilationUnit compilationUnit, ISourceReference sourceReference,
			Class<? extends ASTNode> nodeType) throws JavaModelException {
		return (T) getParent(NodeFinder.perform(compilationUnit, sourceReference.getNameRange()), nodeType);
	}

	private void addFinalFieldModifier(final ASTRewrite astRewrite, final CompilationUnit compilationUnit,
			final IField field) throws JavaModelException {
		final FieldDeclaration fieldDeclaration = getParent(compilationUnit, field, FieldDeclaration.class);
		if (fieldDeclaration != null && !isFinal(fieldDeclaration)) {
			final AST ast = astRewrite.getAST();
			final ListRewrite fieldRewrite = astRewrite.getListRewrite(fieldDeclaration,
					FieldDeclaration.MODIFIERS2_PROPERTY);
			fieldRewrite.insertLast(createFinalModifier(ast), null);
		}
	}

	private boolean isFinal(final FieldDeclaration fieldDeclaration) {
		final List<IExtendedModifier> modifiers = fieldDeclaration.modifiers();
		if (modifiers != null) {
			for (IExtendedModifier modifier : modifiers) {
				if (modifier.isModifier() && modifier instanceof Modifier) {
					if (((Modifier) modifier).isFinal()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private MethodDeclaration createGetterMethod(final AST ast, final IField field, final String methodName)
			throws JavaModelException {
		final Block body = ast.newBlock();
		body.statements().add(createFieldReturnStatement(ast, field));
		final MethodDeclaration methodDeclaration = ast.newMethodDeclaration();
		methodDeclaration.setName(ast.newSimpleName(methodName));
		methodDeclaration.setReturnType2(createType(ast, field));
		methodDeclaration.setBody(body);
		methodDeclaration.modifiers().add(createPublicModifier(ast));

		return methodDeclaration;
	}

	private ReturnStatement createFieldReturnStatement(final AST ast, final IField field) {
		return createReturnStatement(ast, createFieldAccess(ast, field));
	}

	private ReturnStatement createThisReturnStatement(final AST ast) {
		return createReturnStatement(ast, ast.newThisExpression());
	}

	private ReturnStatement createReturnStatement(final AST ast, final Expression expression) {
		final ReturnStatement returnStatement = ast.newReturnStatement();
		returnStatement.setExpression(expression);

		return returnStatement;
	}

	private Expression createFieldAccess(final AST ast, final IField field) {
		if (codeGenerationSettings.qualifyFieldAccessWithThis) {
			return createThisPrefixedFieldAccess(ast, field);
		}

		return createSimpleName(ast, field);
	}

	private FieldAccess createThisPrefixedFieldAccess(final AST ast, final IField field) {
		final FieldAccess fieldAccess = ast.newFieldAccess();
		fieldAccess.setExpression(ast.newThisExpression());
		fieldAccess.setName(createSimpleName(ast, field));

		return fieldAccess;
	}

	private MethodDeclaration createBuilderFieldAssignment(final AST ast, final IField field)
			throws JavaModelException {
		final Block body = ast.newBlock();
		body.statements().add(ast.newExpressionStatement(createAssignment(ast, field)));
		body.statements().add(createThisReturnStatement(ast));
		final String fieldName = field.getElementName();
		final String name = "with" + Character.toUpperCase(fieldName.charAt(0))
				+ fieldName.substring(1, fieldName.length());

		final MethodDeclaration methodDeclaration = ast.newMethodDeclaration();
		methodDeclaration.setName(ast.newSimpleName(name));
		methodDeclaration.setReturnType2(createBuilderType(ast));
		methodDeclaration.setBody(body);
		methodDeclaration.modifiers().add(createPublicModifier(ast));
		methodDeclaration.parameters().add(createParameter(ast, field));

		return methodDeclaration;
	}

	private MethodDeclaration createBuildMethod(final AST ast, final MethodDeclaration constructorToInvoke,
			final IType parentClass) {
		final ClassInstanceCreation instance = ast.newClassInstanceCreation();
		instance.setType(createSimpleType(ast, parentClass.getElementName().toString()));

		final List<SingleVariableDeclaration> constructorParameters = constructorToInvoke.parameters();

		if (constructorParameters != null) {
			for (SingleVariableDeclaration param : constructorParameters) {
				instance.arguments().add(ast.newSimpleName(param.getName().toString()));
			}
		}

		final Block body = ast.newBlock();
		body.statements().add(createReturnStatement(ast, instance));

		final MethodDeclaration buildMethod = ast.newMethodDeclaration();
		buildMethod.modifiers().add(createPublicModifier(ast));
		buildMethod.setName(ast.newSimpleName("build"));
		buildMethod.setReturnType2(createSimpleType(ast, parentClass.getElementName().toString()));
		buildMethod.setBody(body);

		return buildMethod;
	}

	private MethodDeclaration createBuilderAccessor(final AST ast) throws JavaModelException {
		final ClassInstanceCreation builderInstance = ast.newClassInstanceCreation();
		builderInstance.setType(createBuilderType(ast));

		final Block body = ast.newBlock();
		body.statements().add(createReturnStatement(ast, builderInstance));

		final MethodDeclaration methodDeclaration = ast.newMethodDeclaration();
		methodDeclaration.setName(ast.newSimpleName("builder"));
		methodDeclaration.setReturnType2(createBuilderType(ast));
		methodDeclaration.setBody(body);
		methodDeclaration.modifiers().add(createPublicModifier(ast));
		methodDeclaration.modifiers().add(createStaticModifier(ast));

		return methodDeclaration;
	}

	private SimpleType createBuilderType(final AST ast) {
		return ast.newSimpleType(ast.newSimpleName(BUILDER_TYPE_NAME));
	}

	private SimpleName createSimpleName(final AST ast, final IField field) {
		return ast.newSimpleName(field.getElementName());
	}

	private Type createType(final AST ast, final IField field) throws JavaModelException {
		final String typeName = Signature.getSignatureSimpleName(field.getTypeSignature());
		final Code primitiveTypeCode = PrimitiveType.toCode(typeName);

		if (primitiveTypeCode != null) {
			return ast.newPrimitiveType(primitiveTypeCode);
		}

		return createSimpleType(ast, typeName);
	}

	private SimpleType createSimpleType(final AST ast, final String typeName) {
		return ast.newSimpleType(ast.newName(typeName));
	}

	private FieldDeclaration createPublicFinalField(final AST ast, final IField field) throws JavaModelException {
		return createField(ast, field, Arrays.asList(createPublicModifier(ast), createFinalModifier(ast)));
	}

	private FieldDeclaration createField(final AST ast, final IField field,
			final Collection<IExtendedModifier> modifiers) throws JavaModelException {
		final VariableDeclarationFragment variableDeclarationFragment = ast.newVariableDeclarationFragment();
		variableDeclarationFragment.setName(createSimpleName(ast, field));

		final FieldDeclaration fieldDeclaration = ast.newFieldDeclaration(variableDeclarationFragment);
		fieldDeclaration.modifiers().addAll(modifiers);
		fieldDeclaration.setType(createType(ast, field));

		return fieldDeclaration;
	}

	private Modifier createFinalModifier(final AST ast) {
		return ast.newModifier(ModifierKeyword.FINAL_KEYWORD);
	}

	private Modifier createPublicModifier(final AST ast) {
		return ast.newModifier(ModifierKeyword.PUBLIC_KEYWORD);
	}

	private Modifier createStaticModifier(final AST ast) {
		return ast.newModifier(ModifierKeyword.STATIC_KEYWORD);
	}

	private MethodDeclaration createConstructor(final String name, final IField[] fields, final ListRewrite listRewrite,
			final ASTRewrite astRewrite, final Collection<IExtendedModifier> modifiers) throws CoreException {
		final AST ast = astRewrite.getAST();
		final Block body = ast.newBlock();
		final Collection<SingleVariableDeclaration> parameters = new ArrayList<>();

		if (fields != null) {
			for (IField field : fields) {
				body.statements().add(createFieldInitialization(field, listRewrite, astRewrite));
				parameters.add(createParameter(ast, field));
			}
		}

		final MethodDeclaration constructor = ast.newMethodDeclaration();
		constructor.setConstructor(true);
		constructor.modifiers().addAll(modifiers);
		constructor.setName(ast.newSimpleName(name));
		constructor.setBody(body);
		constructor.parameters().addAll(parameters);

		return constructor;
	}

	private Statement createFieldInitialization(final IField field, final ListRewrite listRewrite,
			final ASTRewrite astRewrite) throws JavaModelException {
		final AST ast = astRewrite.getAST();
		final FieldDeclaration fieldNode = (FieldDeclaration) getNode(listRewrite, field);
		final VariableDeclarationFragment variableDeclaration = (VariableDeclarationFragment) fieldNode.fragments()
				.iterator().next();

		final Expression variableInitializer = variableDeclaration.getInitializer();
		if (variableInitializer == null) {
			return ast.newExpressionStatement(createAssignment(ast, field));
		} else {
			Statement initalizationStatement = null;
			if (fieldNode.getType().isPrimitiveType()) {
				initalizationStatement = ast.newExpressionStatement(createAssignment(ast, field));
			} else {
				final IfStatement ifStatement = ast.newIfStatement();
				ifStatement.setExpression(createNotNullCheck(ast, field));
				ifStatement.setThenStatement(ast.newExpressionStatement(createAssignment(ast, field)));
				ifStatement.setElseStatement(ast.newExpressionStatement(
						createAssignment(ast, field, (Expression) ASTNode.copySubtree(ast, variableInitializer))));
				initalizationStatement = ifStatement;
			}

			astRewrite.remove(variableInitializer, null);

			return initalizationStatement;
		}
	}

	private InfixExpression createNotNullCheck(final AST ast, final IField field) {
		final InfixExpression nullCheck = ast.newInfixExpression();
		nullCheck.setLeftOperand(createSimpleName(ast, field));
		nullCheck.setOperator(InfixExpression.Operator.NOT_EQUALS);
		nullCheck.setRightOperand(ast.newNullLiteral());

		return nullCheck;
	}

	private SingleVariableDeclaration createParameter(final AST ast, final IField field) throws JavaModelException {
		final SingleVariableDeclaration parameter = ast.newSingleVariableDeclaration();
		parameter.setName(createSimpleName(ast, field));
		parameter.setType(createType(ast, field));

		if (codeGenerationSettings.makeParametersFinal) {
			parameter.modifiers().add(createFinalModifier(ast));
		}

		return parameter;
	}

	private Assignment createAssignment(final AST ast, final IField field) {
		return createAssignment(ast, field, createSimpleName(ast, field));
	}

	private Assignment createAssignment(final AST ast, final IField field, final Expression expression) {
		final Assignment assignment = ast.newAssignment();
		assignment.setLeftHandSide(createThisPrefixedFieldAccess(ast, field));
		assignment.setRightHandSide(expression);
		assignment.setOperator(Assignment.Operator.ASSIGN);

		return assignment;
	}

	private String getType(final IField field) throws JavaModelException {
		return getType(field.getTypeSignature());
	}

	private String getType(final String type) {
		return Signature.getSimpleName(Signature.toString(type));
	}

	private void removeMethod(final IMethod accessor, final ListRewrite rewrite) throws JavaModelException {
		if (accessor != null) {
			final MethodDeclaration declaration = (MethodDeclaration) getParent(
					NodeFinder.perform(rewrite.getParent().getRoot(), accessor.getNameRange()),
					MethodDeclaration.class);
			if (declaration != null) {
				rewrite.remove(declaration, null);
			}
		}
	}

	private IMethod findMethod(final String name, final IType type, final String[] paramTypes,
			final boolean isConstructor) throws JavaModelException {
		IMethod[] methods = type.getMethods();
		for (int i = 0; i < methods.length; i++) {
			if (isSameMethodSignature(name, paramTypes, isConstructor, methods[i])) {
				return methods[i];
			}
		}

		return null;
	}

	private boolean isSameMethodSignature(final String name, final String[] paramTypes, final boolean isConstructor,
			final IMethod method) throws JavaModelException {
		if (isConstructor || name.equals(method.getElementName())) {
			if (isConstructor == method.isConstructor()) {
				String[] currParamTypes = method.getParameterTypes();
				if (paramTypes.length == currParamTypes.length) {
					for (int i = 0; i < paramTypes.length; i++) {
						if (!paramTypes[i].equals(getType(currParamTypes[i]))) {
							return false;
						}
					}

					return true;
				}
			}
		}
		return false;
	}

	private String[] getFieldsTypes() throws JavaModelException {
		if (fields != null && fields.length > 0) {
			final String[] parameterTypes = new String[fields.length];
			for (int i = 0; i < fields.length; i++) {
				parameterTypes[i] = Signature.getSignatureSimpleName(fields[i].getTypeSignature());
			}

			return parameterTypes;
		}
		return new String[0];
	}
}