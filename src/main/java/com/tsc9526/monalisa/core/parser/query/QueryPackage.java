/*******************************************************************************************
 *	Copyright (c) 2016, zzg.zhou(11039850@qq.com)
 * 
 *  Monalisa is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU Lesser General Public License as published by
 *	the Free Software Foundation, either version 3 of the License, or
 *	(at your option) any later version.

 *	This program is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU Lesser General Public License for more details.

 *	You should have received a copy of the GNU Lesser General Public License
 *	along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************************/
package com.tsc9526.monalisa.core.parser.query;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.tsc9526.monalisa.core.datasource.DBConfig;
import com.tsc9526.monalisa.core.logger.Logger;
import com.tsc9526.monalisa.core.parser.executor.SQLClass;
import com.tsc9526.monalisa.core.parser.jsp.Jsp;
import com.tsc9526.monalisa.core.parser.jsp.JspElement;
import com.tsc9526.monalisa.core.parser.jsp.JspFunction;
import com.tsc9526.monalisa.core.parser.jsp.JspPage;
import com.tsc9526.monalisa.core.parser.jsp.JspPageOut;
import com.tsc9526.monalisa.core.parser.jsp.JspText;
import com.tsc9526.monalisa.core.tools.JavaWriter;

/**
 * 
 * @author zzg.zhou(11039850@qq.com)
 */
public class QueryPackage {
	static Logger logger=Logger.getLogger(QueryPackage.class.getName());
	
	public static String DEFAULT_PACKAGE_NAME="mqs";
	public static String DEFAULT_CLASS_NAME  ="SQL";
	  
	private String comments;
 	
	private String packageName=DEFAULT_PACKAGE_NAME;
	private String className=DEFAULT_CLASS_NAME;
	
	private String db;
	
	private List<JspPage> imports=new ArrayList<JspPage>();
	private List<JspFunction> functions=new ArrayList<JspFunction>(); 
	private List<QueryStatement> statements=new ArrayList<QueryStatement>();
 	
	private Jsp jsp;
	public QueryPackage(Jsp jsp){
		this.jsp=jsp;
		
		processStatements();
	}
	
	public void write(JavaWriter writer){
		writer.write("package "+SQLClass.PACKAGE_PREFIX+"."+packageName+";\r\n\r\n");
	 	
		writer.write("import "+DBConfig.class.getName()+";\r\n");
		writer.write("import "+JspPageOut.class.getName()+";\r\n");
		
		for(JspPage page:imports){
			for(String i:page.getImports()){
				writer.write("import "+i+";\r\n");
			}
		}
		
		writer.write("\r\npublic class "+className+"{\r\n");
		writer.write("\t public final static long TS="+jsp.getLastModified()+"L;\r\n\r\n");
		
		for(QueryStatement q:statements){
			q.write(writer);
		}
		for(JspFunction f:functions){
			writer.write(f.getCode());
		}
		writer.write("}");
	}
	
	
	private void processStatements(){
		String queryXml=translateQueryXml();
		
		processQueryXml(queryXml);
		
		for(JspElement e:jsp.getElements()){
			if(e instanceof JspPage){
				imports.add( (JspPage)e );
			}else if(e instanceof JspFunction){
				functions.add((JspFunction)e);
			}
		}
	}
	
	private void processQueryXml(String xml){
		try{
			Document doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
		
			Node query=null;
			NodeList rs=doc.getChildNodes();
			List<Node> commentNodes=new ArrayList<Node>();
			for(int i=0;i<rs.getLength();i++){
				Node node=rs.item(i);
				 
				if(node.getNodeType()==Node.COMMENT_NODE){
					commentNodes.add(node);
				}else if(node.getNodeType()==Node.ELEMENT_NODE && node.getNodeName().equals("query")){
					query=node;
					break; 
				}
			}
			
			if(query==null){
				throw new RuntimeException("The <query> node not found!");
			}
			
			comments=parseCommentsNode(commentNodes);
			
			parseQueryNode(query);
			 
		}catch(Exception e){
			throw new RuntimeException(e);
		}
	}
	
	private String parseCommentsNode(List<Node> commentNodes){
		if(commentNodes!=null && commentNodes.size()>0){
			StringBuffer sb=new StringBuffer();
			for(Node n:commentNodes){
				sb.append(n.getTextContent()+"\r\n");
			}
		
			return sb.toString();
		}else{
			return null;
		}
	}
	
	 
	
	private void parseQueryNode(Node query){
		parseQueryNodeAttrs(query);
		
		List<Node> commentNodes=new ArrayList<Node>();

		NodeList rs=query.getChildNodes();
		for(int i=0;i<rs.getLength();i++){
			Node node=rs.item(i);
			 
			if(node.getNodeType()==Node.COMMENT_NODE){
				commentNodes.add(node);
			}else if(node.getNodeType()==Node.ELEMENT_NODE){
				if(node.getNodeName().equals("q")){
					NamedNodeMap attrs=node.getAttributes();
							
					QueryStatement qs=new QueryStatement();
					qs.setQueryPackage(this);
					qs.setComments(parseCommentsNode(commentNodes));
				 
					Node id=attrs.getNamedItem("id");
					if(id==null){
						throw new RuntimeException("Missing attribute \"id\"");
					}
					qs.setId(id.getTextContent());
										
					Node resultClassNode=attrs.getNamedItem("resultClass");
					if(resultClassNode!=null){
						String rc=getNodeText(resultClassNode);
						if(rc.trim().length()>0){
							rc=rc.trim();
							if(rc.indexOf(".")<0){
								rc=this.getPackageName()+"."+rc;
							}
							qs.setResultClass(rc);
						}
					}
					
					Node db=attrs.getNamedItem("db");
					if(db!=null){
						qs.setDb(getNodeText(db));
					}
					
					parseJspElements(qs,node);
					
					
					statements.add(qs);
					
					commentNodes.clear();
				}
			}
		}
	}
	
	
	private void parseJspElements(QueryStatement qs,Node node){
		String txt=node.getTextContent();
		
		int p2=0;
		while(true){
			int p1=txt.indexOf("%{",p2);
			if(p1>0){
				String v=txt.substring(p2,p1);
				 
				qs.add(new JspText(jsp, 0, 0).parseCode(v));
				
				p2=txt.indexOf("}%",p1);
				
				String var=txt.substring(p1+2,p2);
				int x=var.indexOf("#");
				int index=Integer.parseInt( var.substring(x+1) );
				
				JspElement e=this.jsp.getElement(index);
				qs.add(e);
				
				p2+=2;
			}else{ 
				qs.add(new JspText(jsp, 0, 0).parseCode(txt.substring(p2)));
				break;
			}
		} 
	}
	
	private void parseQueryNodeAttrs(Node node){
		NamedNodeMap attrs=node.getAttributes();
		Node pkg=attrs.getNamedItem("namespace");
		
		if(pkg==null){
			pkg=attrs.getNamedItem("package");
		}
		
		if(pkg!=null){
			String namespace=getNodeText(pkg).trim();
			if(namespace.length()>0){
				int x=namespace.lastIndexOf(".");
				if(x>0){
					packageName=namespace.substring(0,x);
					className=namespace.substring(x+1);
				}else{
					className=namespace;
				}
			}
		}

		Node db=attrs.getNamedItem("db");
		if(db!=null){
			setDb(getNodeText(db));
		}
	}
	
	private String getNodeText(Node node){
		StringBuffer sb=new StringBuffer();
		
		String txt=node.getTextContent();
		
		int p2=0;
		while(true){
			int p1=txt.indexOf("%{",p2);
			if(p1>=0){
				sb.append(txt.substring(p2,p1));
				
				p2=txt.indexOf("}%");
				
				String var=txt.substring(p1+2,p2);
				int x=var.indexOf("#");
				int index=Integer.parseInt( var.substring(x+1) );
				
				JspElement e=this.jsp.getElement(index);
				sb.append(e.getCode());
				
				p2+=2;
			}else{ 
				sb.append(txt.substring(p2));
				break;
			}
		}
		
		return sb.toString();
	}
	
	private String translateQueryXml(){
		StringBuffer xml=new StringBuffer();
		for(JspElement e:jsp.getElements()){
			if(e instanceof JspText){
				String s=e.getCode();
				for(int i=0;i<s.length();i++){
					char c=s.charAt(i);
					
					if(c=='<' && i<s.length()-1){ 
						char x=s.charAt(i+1);
						if(  (x>='a' && x<'z') || (x>='A' && x<'Z') || x=='!' || x=='/') {
							xml.append(c);
						}else{
							xml.append("&lt;");
						}
					}else{
						xml.append(c);
					}
				}
			}else{
				String tag="%{"+e.getClass().getSimpleName()+"#"+e.getIndex()+"}%";
				xml.append(tag);
			}
		}
		
		int x1=xml.indexOf("<");
		int x2=xml.lastIndexOf(">");
		if (x1<0 || x2<x1) {
			throw new RuntimeException("xml tag \"<query>\" not found!");
		} 
	 	
		String x=xml.substring(x1,x2+1);
		//System.out.println(x);
		return x;
	}
	
	 
	public String getComments() {
		return comments;
	}


	public void setComments(String comments) {
		this.comments = comments;
	}

	public String getPackageName() {
		return packageName;
	}


	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public String getDb() {
		return db;
	}


	public void setDb(String db) {
		this.db = db;
	}


	public List<JspPage> getImports() {
		return imports;
	}


	public void setImports(List<JspPage> imports) {
		this.imports = imports;
	}


	public List<JspFunction> getFunctions() {
		return functions;
	}


	public void setFunctions(List<JspFunction> functions) {
		this.functions = functions;
	}


	public List<QueryStatement> getStatements() {
		return statements;
	}


	public void setStatements(List<QueryStatement> statements) {
		this.statements = statements;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

}
