/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.flex.compiler.internal.graph;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.io.FileUtils;
import org.apache.flex.compiler.clients.problems.ProblemQuery;
import org.apache.flex.compiler.internal.codegen.js.goog.JSGoogEmitterTokens;
import org.apache.flex.compiler.internal.driver.js.goog.JSGoogConfiguration;
import org.apache.flex.compiler.problems.FileNotFoundProblem;

import com.google.common.io.Files;

public class GoogDepsWriter {

	public GoogDepsWriter(File outputFolder, String mainClassName, JSGoogConfiguration config)
	{
		this.outputFolderPath = outputFolder.getAbsolutePath();
		this.mainName = mainClassName;
		otherPaths = config.getSDKJSLib();
		otherPaths.add(new File(outputFolder.getParent(), "flexjs/FlexJS/src").getPath());
	}
	
	private ProblemQuery problems;
	private String outputFolderPath;
	private String mainName;
	private List<String> otherPaths;
	private boolean problemsFound = false;
	private ArrayList<GoogDep> dps;
	
	private HashMap<String,GoogDep> depMap = new HashMap<String,GoogDep>();
	
	public ArrayList<String> getListOfFiles() throws InterruptedException
	{
		if (dps == null)
		{
			buildDB();
			dps = sort(mainName);
		}
		ArrayList<String> files = new ArrayList<String>();
		for (GoogDep gd : dps)
		{
			files.add(gd.filePath);
		}
		return files;
	}
	
	public boolean generateDeps(ProblemQuery problems, StringBuilder depsFileData) throws InterruptedException, FileNotFoundException
	{
	    problemsFound = false;
	    this.problems = problems;
	    if (dps == null)
	    {
	    	buildDB();
	    	dps = sort(mainName);
	    }
		String outString = "// generated by FalconJS" + "\n";
		int n = dps.size();
		for (int i = n - 1; i >= 0; i--)
		{
			GoogDep gd = dps.get(i);
			if (!isGoogClass(gd.className)) 
			{
			    String s = "goog.addDependency('";
	            s += relativePath(gd.filePath);
	            s += "', ['";
	            s += gd.className;
	            s += "'], [";
	            s += getDependencies(gd.deps);
	            s += "]);\n";
	            outString += s;
			}
		}
		depsFileData.append(outString);
		return !problemsFound; 
	}
	
	private boolean isGoogClass(String className)
	{
	    return className.startsWith("goog.");
	}
	
	private void buildDB()
	{
		addDeps(mainName);
	}
	
    public ArrayList<String> filePathsInOrder = new ArrayList<String>();
    
    public ArrayList<String> additionalHTML = new ArrayList<String>();
    
    private HashMap<String, GoogDep> visited = new HashMap<String, GoogDep>();
    
	private ArrayList<GoogDep> sort(String rootClassName)
	{
		ArrayList<GoogDep> arr = new ArrayList<GoogDep>();
		GoogDep current = depMap.get(rootClassName);
		sortFunction(current, arr);
		return arr;
	}
	
	private void sortFunction(GoogDep current, ArrayList<GoogDep> arr)
	{
		visited.put(current.className, current);
		
		filePathsInOrder.add(current.filePath);
        System.out.println("Dependencies calculated for '" + current.filePath + "'");

		ArrayList<String> deps = current.deps;
		for (String className : deps)
		{
			if (!visited.containsKey(className) && !isGoogClass(className))
			{
				GoogDep gd = depMap.get(className);
				sortFunction(gd, arr);
			}
		}
		arr.add(current);
	}
	
	private void addDeps(String className)
	{
		if (depMap.containsKey(className) || isGoogClass(className))
			return;
		
		// build goog dependency list
		GoogDep gd = new GoogDep();
		gd.className = className;
		gd.filePath = getFilePath(className);
		if(gd.filePath.isEmpty()) {
			throw new RuntimeException("Unable to find JavaScript filePath for class: " + className);
		}
		depMap.put(gd.className, gd);
		ArrayList<String> deps = getDirectDependencies(gd.filePath);
		
		gd.deps = new ArrayList<String>();
		ArrayList<String> circulars = new ArrayList<String>();
		for (String dep : deps)
		{
		    if (depMap.containsKey(dep) && !isGoogClass(dep))
		    {
		        circulars.add(dep);
		        continue;
		    }
			gd.deps.add(dep);
		}
        for (String dep : deps)
        {
            addDeps(dep);
        }
		if (circulars.size() > 0)
		{
		    // remove requires that would cause circularity
		    try
            {
                List<String> fileLines = Files.readLines(new File(gd.filePath), Charset.defaultCharset());
                ArrayList<String> finalLines = new ArrayList<String>();
                
                FileInfo fi = getBaseClass(fileLines, className);
                int suppressCount = 0;
                int i = 0;
                for (String line : fileLines)
                {
                	if (i < fi.constructorLine)
                	{
	                    int c = line.indexOf(JSGoogEmitterTokens.GOOG_REQUIRE.getToken());
	                    if (c > -1)
	                    {
	                        int c2 = line.indexOf(")");
	                        String s = line.substring(c + 14, c2 - 1);
	                        if (circulars.contains(s) && !s.equals(fi.inherits))
	                        {
	                        	suppressCount++;
	                            continue;
	                        }
	                    }
                	}
                    finalLines.add(line);
                    i++;
                }
                if (suppressCount > 0)
                {
                	if (fi.suppressLine > 0)
                	{
                		if (fi.suppressLine < fi.constructorLine) 
                		{
	                		String line = finalLines.get(fi.suppressLine);
	                		int c = line.indexOf("@suppress {");
	                		if (c > -1)
	                		{
	                			if (!line.contains("missingRequire"))
	                			{
	                				line = line.substring(0, c) + "@suppress {missingRequire|" + line.substring(c + 11);
	                				finalLines.remove(fi.suppressLine);
	                				finalLines.add(fi.suppressLine, line);
	                			}
	                		}
	                		else
	                			System.out.println("Confused by @suppress in " + className);
	                	}
	                	else                		
	                	{
	                		// the @suppress was for the constructor or some other thing so add a top-level
	                		// @suppress
	                		if (fi.fileoverviewLine > -1)
	                		{
	                			// there is already a fileOverview but no @suppress
	                			finalLines.add(fi.fileoverviewLine + 1, " *  @suppress {missingRequire}");
	                		}
	                		else if (fi.googProvideLine > -1)
	                		{
	                			finalLines.add(fi.googProvideLine, " */");
	                			finalLines.add(fi.googProvideLine, " *  @suppress {missingRequire}");
	                			finalLines.add(fi.googProvideLine, " *  @fileoverview");
	                			finalLines.add(fi.googProvideLine, "/**");
	                		}
	                		else
	                		{
	                			System.out.println("Confused by @suppress in " + className);
	                		}
	                	}
                	}
                	else
                	{
                		if (fi.fileoverviewLine > -1)
                		{
                			// there is already a fileoverview but no @suppress
                			finalLines.add(fi.fileoverviewLine + 1, " *  @suppress {missingRequire}");
                		}
                		else if (fi.googProvideLine > -1)
                		{
                			finalLines.add(fi.googProvideLine, " */");
                			finalLines.add(fi.googProvideLine, " *  @suppress {missingRequire}");
                			finalLines.add(fi.googProvideLine, " *  @fileoverview");
                			finalLines.add(fi.googProvideLine, "/**");
                		}
                		else
                		{
                			System.out.println("Confused by @suppress in " + className);
                		}                		
                	}
                }
                File file = new File(gd.filePath);  
                PrintWriter out = new PrintWriter(new FileWriter(file));  
                for (String s : finalLines)
                {
                    out.println(s);
                }
                out.close();
                    
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
		    
		}
	}
	
	FileInfo getBaseClass(List<String> lines, String className)
	{
		FileInfo fi = new FileInfo();
		
	    int n = lines.size();
	    fi.constructorLine = n;
	    fi.suppressLine = -1;
	    fi.fileoverviewLine = -1;
	    fi.googProvideLine = -1;
	    for (int i = 0; i < n; i++)
	    {
	        String line = lines.get(i);
	        int c2;
	        int c = line.indexOf("goog.inherits");
	        if (c > -1)
	        {
	            String inheritLine = ""; 
                while (true)
                {
                    inheritLine += line;
                    c2 = line.indexOf(")");
                    if (c2 > -1)
                        break;
                    else
                    {
                        i++;
                        line = lines.get(i);
                    }
                }
	            c = inheritLine.indexOf(",");
                c2 = inheritLine.indexOf(")");
                fi.inherits = inheritLine.substring(c + 1, c2).trim();
                return fi;
	        }
	        else
	        {
		        c = line.indexOf("@constructor");
		        if (c > -1)
		        	fi.constructorLine = i;
		        else
		        {
			        c = line.indexOf("@interface");
			        if (c > -1)
			        	fi.constructorLine = i;
			        else
			        {
			        	c = line.indexOf("@suppress");
			        	if (c > -1)
			        		fi.suppressLine = i;
			        	else
			        	{
				        	c = line.indexOf("@fileoverview");
				        	if (c > -1)
				        		fi.fileoverviewLine = i;
				        	else
				        	{
					        	c = line.indexOf("goog.provide");
					        	if (c > -1)
					        		fi.googProvideLine = i;				        		
				        	}
			        	}
			        }
		        }
	        }
	    }
	    return fi;
	}
	
	String getFilePath(String className)
	{
	    String fn;
	    File destFile;
	    File f;
	    
		String classPath = className.replace("_", File.separator);
		
        fn = outputFolderPath + File.separator + classPath + ".js";
        f = new File(fn);
        if (f.exists())
        {
            return fn;
        }
        
        for (String otherPath : otherPaths)
        {
    		fn = otherPath + File.separator + classPath + ".js";
    		f = new File(fn);
    		if (f.exists())
    		{
    			fn = outputFolderPath + File.separator + classPath + ".js";
    			destFile = new File(fn);
    			// copy source to output
    			try {
    				FileUtils.copyFile(f, destFile);
    				
    				// (erikdebruin) copy class assets files
    				if (className.contains("org_apache_flex"))
    				{
    				    File assetsDir = new File(f.getParentFile(), "assets");
    				    if (assetsDir.exists())
    				    {
    				        String nameOfClass = className.substring(className.lastIndexOf('_') + 1);
    				        
    				        File[] assetsList = assetsDir.listFiles();
					        assert assetsList != null;
					        for (File assetFile : assetsList) {
						        String assetFileName = assetFile.getName();

						        if (assetFile.isFile() && assetFileName.indexOf(nameOfClass) == 0) {
							        String pathOfClass;
							        pathOfClass = className.substring(0, className.lastIndexOf('_'));
							        pathOfClass = pathOfClass.replace("_", File.separator);

							        destFile = new File(outputFolderPath +
									        File.separator + pathOfClass +
									        File.separator + "assets" +
									        File.separator + assetFileName);
							        FileUtils.copyFile(assetFile, destFile);

							        destFile = new File(outputFolderPath.replace("js-debug", "js-release") +
									        File.separator + pathOfClass +
									        File.separator + "assets" +
									        File.separator + assetFileName);
							        FileUtils.copyFile(assetFile, destFile);

							        System.out.println("Copied assets of the '" + nameOfClass + "' class");
						        }
					        }
    				    }
    				}
    			} catch (IOException e) {
    				System.out.println("Error copying file for class: " + className);
    			}
    			return fn;
    		}
        }
        
		System.out.println("Could not find file for class: " + className);
		problems.add(new FileNotFoundProblem(className));
		problemsFound = true;
		return "";
	}
	
	private ArrayList<String> getDirectDependencies(String fn)
	{
		ArrayList<String> deps = new ArrayList<String>();
		
		FileInputStream fis;
		try {
			fis = new FileInputStream(fn);
			Scanner scanner = new Scanner(fis, "UTF-8");
			boolean inInjectHTML = false;
			while (scanner.hasNextLine())
			{
				String s = scanner.nextLine();
				if (s.contains("goog.inherits"))
					break;
                if (inInjectHTML)
                {
                    int c = s.indexOf("</inject_html>");
                    if (c > -1)
                    {
                        inInjectHTML = false;
                        continue;
                    }
                }    
                if (inInjectHTML)
                {
				    additionalHTML.add(s);
				    continue;
                }
				int c = s.indexOf(JSGoogEmitterTokens.GOOG_REQUIRE.getToken());
				if (c > -1)
				{
					int c2 = s.indexOf(")");
					s = s.substring(c + 14, c2 - 1);
					deps.add(s);
				}
                c = s.indexOf("<inject_html>");
                if (c > -1)
                {
                    inInjectHTML = true;
                }
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return deps;
	}
	
	private String getDependencies(ArrayList<String> deps)
	{
		String s = "";
		for (String dep : deps)
		{
			if (s.length() > 0)
			{
				s += ", ";
			}
			s += "'" + dep + "'";			
		}
		return s;
	}

	String relativePath(String path)
	{
        if (path.indexOf(outputFolderPath) == 0)
        {
            path = path.replace(outputFolderPath, "../../..");
        }
        else
        {
    	    for (String otherPath : otherPaths)
    	    {
        		if (path.indexOf(otherPath) == 0)
        		{
        			path = path.replace(otherPath, "../../..");
        			
        		}
    	    }
        }
		// paths are actually URIs and always have forward slashes
		path = path.replace('\\', '/');
		return path;
	}
	private class GoogDep
	{
		public String filePath;
		public String className;
		public ArrayList<String> deps;
		
	}
	private class FileInfo
	{
		public String inherits;
		public int constructorLine;
		public int suppressLine;
		public int fileoverviewLine;
		public int googProvideLine;
	}
}
