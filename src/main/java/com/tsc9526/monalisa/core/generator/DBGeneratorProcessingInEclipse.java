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
package com.tsc9526.monalisa.core.generator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URLClassLoader;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.apt.pluggable.core.dispatch.IdeProcessingEnvImpl;
import org.eclipse.jdt.launching.JavaRuntime;

import com.tsc9526.monalisa.core.datasource.DataSourceManager;
import com.tsc9526.monalisa.core.datasource.DbProp;
import com.tsc9526.monalisa.core.tools.CloseQuietly;
import com.tsc9526.monalisa.core.tools.Helper;

/**
 * 
 * @author zzg.zhou(11039850@qq.com)
 */
public class DBGeneratorProcessingInEclipse extends DBGeneratorProcessing{	 
	private IdeProcessingEnvImpl processingEnv;	 
	private TypeElement typeElement;
	 
	public DBGeneratorProcessingInEclipse(ProcessingEnvironment processingEnv,TypeElement typeElement) {
		super();
		 
		this.processingEnv = (IdeProcessingEnvImpl)processingEnv;		 
		this.typeElement = typeElement;
		 	
	}
	
	public void generateFiles(){
		URLClassLoader loader=null;
		try{
			IJavaProject project=processingEnv.getJavaProject();
			
			String[] classPath=JavaRuntime.computeDefaultRuntimeClassPath(project);
			 
		 	loader=new URLClassLoader(Helper.toURLs(classPath),processingEnv.getClass().getClassLoader());
			Thread.currentThread().setContextClassLoader(loader);
			
			beginProcessing(loader);
			
			String className=DBGeneratorProcessing.class.getName();
			Class<?> clazz=Class.forName(className,true,loader);
			Constructor<?> cs=clazz.getConstructor(ProcessingEnvironment.class,TypeElement.class);
			
			Method m=clazz.getMethod("generateFiles");
			Object dbgp=cs.newInstance(processingEnv,typeElement);
			m.invoke(dbgp);
			
			endProcessing(loader);
		}catch(Exception e){
			throw new RuntimeException(e);
		}finally{
			CloseQuietly.close(loader);
		}
	}
 
	private void beginProcessing(URLClassLoader loader)throws Exception{
		Class<?> dbPropClass=loader.loadClass(DbProp.class.getName());
		dbPropClass.getField("ProcessingEnvironment").set(null, true);
	}
	
	private void endProcessing(URLClassLoader loader)throws Exception{
		Class<?> clazz=loader.loadClass(DataSourceManager.class.getName());
		clazz.getMethod("shutdown").invoke(null);
	}
	   
}
