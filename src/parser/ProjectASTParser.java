package parser;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.api.JavacTool;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import javax.tools.StandardJavaFileManager;

import parser.ClassObject.Abstraction;
import parser.Connection.Type;

public class ProjectASTParser {

	// HashMap containing all ClassObjects
	public static HashMap<String, ClassObject> Classes = new HashMap<String, ClassObject>();

	// For NewClass
	public static ClassObject thisclass = new ClassObject();

	public static Collection<ClassObject> getSortedClasses() {
		ArrayList<ClassObject> classes = new ArrayList<ClassObject>(Classes.values());
		Collections.sort(classes, (a, b) -> a.getName().compareTo(b.getName()));
		return classes;
	}
	
	/**
	 * C++ getchar() lookalike function for debugging purposes
	 */
	public static void getchar() {
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		System.out.print("\r\nPlease press a key : ");
		String username = null;
		try {
			username = reader.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("You entered : " + username + "\r\n");
	}

	/**
	 * Function to find files within the root file
	 * 
	 * @param path String
	 * @param files ArrayList<File>
	 */
	public static void find_files(String path, ArrayList<File> files) {
		File root = new File(path);
		File[] list = root.listFiles();
		if (list == null)
			return;
		for (File f : list) {
			if (f.isDirectory())
				find_files(f.getAbsolutePath(), files);
			else
				files.add(f.getAbsoluteFile());
		}
	}

	/**
	 * Parse function for gui purposes.
	 * 
	 * @param project defines the input project folder
	 * @throws IOException 
	 */
	public static void parse(String project) throws IOException {

		Classes.clear();
		System.setErr(new PrintStream(new ByteArrayOutputStream()));

		// Scanning process
		JavaCompiler compiler = JavacTool.create();
		StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, null);
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();

		String projectPath = project;
		ArrayList<File> files = new ArrayList<File>();
		find_files(projectPath, files);

		// Files in folder
		ArrayList<JavaFileObject> units = new ArrayList<JavaFileObject>();
		for (JavaFileObject unit : manager.getJavaFileObjects(files.toArray(new File[files.size()]))) {
			if (unit.getKind() == Kind.SOURCE)
				units.add(unit);
		}
		ASTParser parser = ASTParser.newParser(AST.JLS12);
		DPVisitor visitor = new DPVisitor();
		for (JavaFileObject unit : units) {
			CharSequence s = unit.getCharContent(true);
			parser.setSource(s.toString().toCharArray());
			CompilationUnit compUnit = (CompilationUnit) parser.createAST(null);
			for (Object typeObj : compUnit.types()) {
				TypeDeclaration type = (TypeDeclaration) typeObj;
				visitor.createClassObject();
				type.accept(visitor);
				ClassObject clas = visitor.getClassObject();
				Classes.put(clas.getName(), clas);
			}
//			compUnit.accept(visitor);
//			for (ClassObject clas : buildClassObject(compUnit)) {
//				Classes.put(clas.getName(), clas);
//			}
		}
//		JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics, null, null, units);
//		SignatureExtractor tscanner = new SignatureExtractor();
//		try {
//			tscanner.scan(task.parse(), null);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}

		for (ClassObject j : Classes.values()) {
			j.findInherits();
			j.findUses();
			j.findCalls();
			j.findCreates();
			j.findHas();
			j.findReferences();
		}
		parser.toString();
	}

	private static List<ClassObject> buildClassObject(CompilationUnit unit) {
		List<ClassObject> classes = new ArrayList<>();
		
		for (Object obj : unit.types()) {
			if (obj instanceof TypeDeclaration) {
				TypeDeclaration type = (TypeDeclaration) obj;
				classes.add(Create_ClassObject(type));
				
				for (TypeDeclaration nested : type.getTypes()) {
					classes.add(Create_ClassObject(nested));
				}
			}
		}
		return classes;
	}
	
	
	/**
	 * Creates a ClassObject from a string, when encountering a new class
	 * 
	 * @param s String
	 */
	public static void Create_ClassObject(String s) {
		if (!(Classes.containsKey(s))) {
			// new ClassObject
			ClassObject newclass = new ClassObject();
			// Setting Name
			newclass.setName(s);
			// Setting abstraction as Unknown
			newclass.set_abstraction(Abstraction.Unknown);
			// Adding to Hashmap
			Classes.put(s, newclass);
		}
	}

	/**
	 * Creates a ClassObject from a ClassTree, object provided from the scan procedure.
	 * 
	 * @param aclass ClassTree
	 */
	public static ClassObject Create_ClassObject(TypeDeclaration aclass) {
		// new ClassObject
		ClassObject newclass = new ClassObject();
		// Setting Name
		newclass.setName(aclass.getName().getFullyQualifiedName());
		return create_fill_body(aclass, newclass);
	}

	/**
	 * Main body of Create_ClassObject and Full_ClassObject functions.
	 * 
	 * @param aclass ClassTree
	 * @param newclass ClassObject
	 */
	public static ClassObject create_fill_body(TypeDeclaration aclass, ClassObject newclass) {
		newclass.set_abstraction(Abstraction.Normal);
		// Distinguishing Interfaces
		if (aclass.isInterface())
			newclass.set_abstraction(Abstraction.Interface);
		// Setting Extends
		if (aclass.getSuperclassType() != null) {
			org.eclipse.jdt.core.dom.Type superClass = aclass.getSuperclassType();
			newclass.setExtends(getTypeName(superClass));
			//newclass.setExtends(superClass.);
		}
		for (Object face : aclass.superInterfaceTypes()) {
			org.eclipse.jdt.core.dom.Type interfaceType = (org.eclipse.jdt.core.dom.Type) face;
			newclass.addImplement(getTypeName(interfaceType));
		}
		// Setting Implements
//		for (Tree t : aclass.get.getImplementsClause()) {
//			newclass.addImplement(t.toString());
//			if (!Classes.containsKey(t.toString()))
//				Create_ClassObject(t.toString());
//		}
		// Setting Modifiers and isAbstract
		for (Object t : aclass.modifiers()) {
			//newclass.addModifier(t.toString());
			if (t.toString().equals("abstract"))
				newclass.set_abstraction(Abstraction.Abstract);
		}
		// Settings Variables
		for (FieldDeclaration var : aclass.getFields()) {
			Variable variable = new Variable();
			String varName = getVariableName(var);
			if (varName != null) {
				variable.setName(varName);
				variable.settype(getTypeName(var.getType()));
				newclass.addMVariable(variable);
			}
		}
		
		// Setting Methods
		for (MethodDeclaration t : aclass.getMethods()) {
			MethodDeclaration method = t;
			Method newmethod = new Method();
			// Set Method name
			newmethod.setName(method.getName().getFullyQualifiedName());
			// Set Method return type
			String s;
			if (method.getReturnType2() == null)
				s = "Void";
			else
				s = getTypeName(method.getReturnType2());
			
			if (s == null) {
				s = "Void";
			}
			newmethod.setReturntype(s);
			
			

			// Check for arrays and/or list of classes
			if (s.contains("<")) {
				String s1 = s.substring(s.indexOf("<") + 1, s.indexOf(">"));
				s = s1;
			} else if (s.contains("[]")) {
				String s2 = s.substring(0, s.indexOf("["));
				s = s2;
			}

			// If you encounter a new class, add it in Classes
			if (!(GeneralMethods.isPrimitive(s)) && (!Classes.containsKey(s)) && (s != newclass.getName())) {
				//Create_ClassObject(s);
			}
			// Set Method Input types
			for (Object m : method.parameters()) {
				SingleVariableDeclaration param = (SingleVariableDeclaration) m;
				s = getTypeName(param.getType());
				newmethod.addInputtype(s);
				
				Variable paramVar = new Variable();
				paramVar.settype(s);
				paramVar.setName(param.getName().getFullyQualifiedName());
				newclass.addMVariable(paramVar);
			}
			// Set Method Modifiers and isAbstract
			newmethod.setisAbstract(false);
			for (Object m : method.modifiers()) {
				//newmethod.addModifier(m.toString());
				if (m.equals(Modifier.ABSTRACT))
					newmethod.setisAbstract(true);
			}
			newclass.addMethod(newmethod);
			
			Block block = method.getBody();
			if (block != null) {
				for (Object objStatement : block.statements()) {
					if (objStatement instanceof ExpressionStatement) {
						ExpressionStatement statement = (ExpressionStatement) objStatement;
						Expression ex = statement.getExpression();
						if (ex instanceof MethodInvocation) {
							MethodInvocation methodInvoke = (MethodInvocation) ex;
							newclass.addMethodInvocation(methodInvoke.toString());
						}
					}
					if (objStatement instanceof VariableDeclarationStatement) {
						VariableDeclarationStatement varStatement = (VariableDeclarationStatement) objStatement;
						if (varStatement.fragments().size() > 0 && varStatement.fragments().get(0) instanceof VariableDeclarationFragment) {
							VariableDeclarationFragment frag = (VariableDeclarationFragment) varStatement.fragments().get(0);
							String varName = frag.getName().getFullyQualifiedName();
							String typeName = getTypeName(varStatement.getType());
							
							Variable var = new Variable();
							var.setName(varName);
							var.settype(typeName);
							newclass.addMVariable(var);
							
							if (frag.getInitializer() != null) {
								Object init = frag.getInitializer();
								if (init instanceof ClassInstanceCreation) {
									ClassInstanceCreation creation = (ClassInstanceCreation) init;
									newclass.addNew_Instance(getTypeName(creation.getType()));
								}
							}
						}
					}
				}
			}
		}
		return newclass;
//		// For NewClass
//		thisclass = newclass;
//		// Adding to Hashmap
//		if (Classes.containsKey(newclass.getName())) {
//			// Since we checked earlier, the only way a ClassObject with the same name exists,
//			// is that it was created during this function(create_fill_body)
//			// countClassObjectStringcreated--;
//			Classes.remove(newclass.getName());
//			Classes.put(newclass.getName(), newclass);
//		} else {
//			Classes.put(newclass.getName(), newclass);
//		}
	}
	
	private static class DPVisitor extends ASTVisitor {
		
		private ClassObject current;
		
		public void createClassObject() {
			current = new ClassObject();
		}
		
		public ClassObject getClassObject() {
			return current;
		}
		
		@Override
		public boolean visit(TypeDeclaration node) {
			current.setName(node.getName().getFullyQualifiedName());
			current.set_abstraction(Abstraction.Normal);
			// Distinguishing Interfaces
			if (node.isInterface())
				current.set_abstraction(Abstraction.Interface);
			// Setting Extends
			if (node.getSuperclassType() != null) {
				org.eclipse.jdt.core.dom.Type superClass = node.getSuperclassType();
				current.setExtends(getTypeName(superClass));
			}
			for (Object face : node.superInterfaceTypes()) {
				org.eclipse.jdt.core.dom.Type interfaceType = (org.eclipse.jdt.core.dom.Type) face;
				current.addImplement(getTypeName(interfaceType));
			}
			for (Object modifier : node.modifiers()) {
				current.addModifier(modifier.toString());
				if (modifier.toString().equals("abstract"))
					current.set_abstraction(Abstraction.Abstract);
			}
			
			return true;
		}
		
		@Override
		public boolean visit(FieldDeclaration node) {
			Variable variable = new Variable();
			String varName = getVariableName(node);
			if (varName != null) {
				variable.setName(varName);
				variable.settype(getTypeName(node.getType()));
				current.addMVariable(variable);
			}
			return true;
		}
		
		@Override
		public boolean visit(MethodDeclaration node) {
			Method newmethod = new Method();
			// Set Method name
			newmethod.setName(node.getName().getFullyQualifiedName());
			// Set Method return type
			String s;
			if (node.getReturnType2() == null)
				s = "Void";
			else
				s = getTypeName(node.getReturnType2());
			
			if (s == null) {
				s = "Void";
			}
			newmethod.setReturntype(s);
			
			// Check for arrays and/or list of classes
			if (s.contains("<")) {
				String s1 = s.substring(s.indexOf("<") + 1, s.indexOf(">"));
				s = s1;
			} else if (s.contains("[]")) {
				String s2 = s.substring(0, s.indexOf("["));
				s = s2;
			}
			// Set Method Input types
			for (Object m : node.parameters()) {
				SingleVariableDeclaration param = (SingleVariableDeclaration) m;
				s = getTypeName(param.getType());
				newmethod.addInputtype(s);
				
				Variable paramVar = new Variable();
				paramVar.settype(s);
				paramVar.setName(param.getName().getFullyQualifiedName());
				current.addMVariable(paramVar);
			}
			// Set Method Modifiers and isAbstract
			newmethod.setisAbstract(false);
			for (Object m : node.modifiers()) {
				//newmethod.addModifier(m.toString());
				if (m.equals(Modifier.ABSTRACT))
					newmethod.setisAbstract(true);
			}
			current.addMethod(newmethod);
			
			return true;
		}
		
		@Override
		public boolean visit(VariableDeclarationStatement node) {
			if (node.fragments().size() > 0 && node.fragments().get(0) instanceof VariableDeclarationFragment) {
				VariableDeclarationFragment frag = (VariableDeclarationFragment) node.fragments().get(0);
				String varName = frag.getName().getFullyQualifiedName();
				String typeName = getTypeName(node.getType());
				
				Variable var = new Variable();
				var.setName(varName);
				var.settype(typeName);
				current.addMVariable(var);
			}
			return true;
		}
		
		@Override
		public boolean visit(ClassInstanceCreation node) {
			current.addNew_Instance(getTypeName(node.getType()));
			return true;
		}
		
		@Override
		public boolean visit(MethodInvocation node) {
			String text = node.toString();
			if (text.contains("(")) {
				text = text.substring(0, text.indexOf("("));
			}
			current.addMethodInvocation(text);
			return true;
		}
	}
	
	private static String getVariableName(FieldDeclaration field) {
		Object o = field.fragments().get(0);
		if(o instanceof VariableDeclarationFragment){
			String s = ((VariableDeclarationFragment) o).getName().toString();
			if (s.contains("<")) {
				String s1 = s.substring(s.indexOf("<") + 1, s.indexOf(">"));
				s = s1;
			} else if (s.contains("[]")) {
				String s2 = s.substring(0, s.indexOf("["));
				s = s2;
			}
			return s;
		}
		return null;
	}
	
	private static String getTypeName(org.eclipse.jdt.core.dom.Type type) {
		return type.toString();
//		if (type.isSimpleType()) {
//			SimpleType s = (SimpleType) type;
//			return s.getName().getFullyQualifiedName();
//		}
//		if (type.isNameQualifiedType()) {
//			NameQualifiedType t = (NameQualifiedType) type;
//			return t.getName().getFullyQualifiedName();
//		}
//		return null;
	}

	/**
	 * Print uses of a specific ClassObject -- Need to use ClassObject.finduses() first
	 * 
	 * @param c ClassObject
	 */
	public static void print_uses(ClassObject c) {
		// Print uses
		for (Connection s : c.getConnections(Type.uses)) {
			ClassObject c1 = s.getTo();
			System.out.println(c.get_abstraction() + " " + c.getName() + " uses " + c1.get_abstraction() + " "
					+ c1.getName());
		}
	}

	/**
	 * Print inherits of a specific ClassObject -- Need to use ClassObject.findinherits() first
	 * 
	 * @param c ClassObject
	 */
	public static void print_inherits(ClassObject c) {
		// Print inherits
		for (Connection s : c.getConnections(Type.inherits)) {
			ClassObject c1 = s.getTo();
			System.out.println(c.get_abstraction() + " " + c.getName() + " inherits " + c1.get_abstraction() + " "
					+ c1.getName());
		}
	}

	/**
	 * Print has of a specific ClassObject -- Need to use ClassObject.findhas() first
	 * 
	 * @param c ClassObject
	 */
	public static void print_has(ClassObject c) {
		// Print has
		for (Connection s : c.getConnections(Type.has)) {
			ClassObject c1 = s.getTo();
			System.out.println(c.get_abstraction() + " " + c.getName() + " has " + c1.get_abstraction() + " "
					+ c1.getName());
		}
	}

	/**
	 * Print references of a specific ClassObject -- Need to use ClassObject.findrefs() first
	 * 
	 * @param c ClassObject
	 */
	public static void print_references(ClassObject c) {
		// Print references
		for (Connection s : c.getConnections(Type.references)) {
			ClassObject c1 = s.getTo();
			System.out.println(c.get_abstraction() + " " + c.getName() + " references " + c1.get_abstraction() + " "
					+ c1.getName());
		}
	}

	/**
	 * Print calls of a specific ClassObject -- Need to use ClassObject.findcalls() first
	 * 
	 * @param c ClassObject
	 */
	public static void print_calls(ClassObject c) {
		// Print calls
		for (Connection s : c.getConnections(Type.calls)) {
			ClassObject c1 = s.getTo();
			System.out.println(c.get_abstraction() + " " + c.getName() + " calls " + c1.get_abstraction() + " "
					+ c1.getName());
		}
	}

	/**
	 * Print creates of a specific ClassObject -- Need to use ClassObject.findcreates()
	 * 
	 * @param c ClassObject
	 */
	public static void print_creates(ClassObject c) {
		// Print creates
		for (Connection s : c.getConnections(Type.creates)) {
			ClassObject c1 = s.getTo();
			System.out.println(c.get_abstraction() + " " + c.getName() + " creates " + c1.get_abstraction() + " "
					+ c1.getName());
		}
	}
}