/*******************************************************************************
 * Copyright (c) 2008, 2013
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Javier Canovas (javier.canovas@inria.fr) 
 *******************************************************************************/

package fr.inria.atlanmod.json.web;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;

import fr.inria.atlanmod.discoverer.JsonComposer;
import fr.inria.atlanmod.discoverer.JsonDiscoverer;
import fr.inria.atlanmod.json.Model;

/**
 * Main servlet to provide access to the composer
 * 
 * @author Javier Canovas (javier.canovas@inria.fr)
 *
 */
@WebServlet("/compose")
public class JsonComposerServlet extends AbstractJsonDiscoverer {
	private static final long serialVersionUID = 1L;
	private static final String COMPOSER_ID = "IdComposer";

	private static String paramsPattern = Pattern.quote("sources[") + "([a-zA-Z]*)"+ Pattern.quote("][") + "[\\$a-zA-Z]*" + Pattern.quote("]") + "(" + Pattern.quote("[]") + ")?";

	/* 
	 * Performs a POST call and returns a String in base64 with the picture of the metamodel
	 * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// 1. Disgesting the params
		Pattern pattern = Pattern.compile(paramsPattern);
		HashMap<String, List<String>> sources = new HashMap<String, List<String>>();
		for (Enumeration<String> e = request.getParameterNames() ; e.hasMoreElements() ;) {
			String paramName = e.nextElement();
			Matcher matcher = pattern.matcher(paramName);
			if(matcher.find()) {
				String sourceName = matcher.group(1);
				String[] valuesParam = request.getParameterValues("sources[" + sourceName + "][jsonDefs][]");
				List<String> sourcesList = sources.get(sourceName);
				if(sourcesList == null) sources.put(sourceName, (sourcesList = new ArrayList<String>()));
				for(String valueParam : valuesParam) {
					sourcesList.add(valueParam);
				}

			}
		}
		if(sources.size() == 0) throw new ServletException("No params in the call");

		// 2. Discovery
		List<EPackage> metamodels = new ArrayList<EPackage>();
		for (String sourceName : sources.keySet()) {
			List<String> sourcesList = sources.get(sourceName);
			JsonDiscoverer discoverer = new JsonDiscoverer();
			EPackage metamodel = null;
			for(int i = 0; i < sourcesList.size(); i++) {
				String jsonSource = sourcesList.get(i);
				if(i == 0) 
					metamodel = discoverer.discoverMetamodel(jsonSource);
				else 
					metamodel = discoverer.refineMetamodel(jsonSource, metamodel);
			}
			if(metamodel != null) 
				metamodels.add(metamodel);
		}
		
		// 3. Composition
		JsonComposer composer = new JsonComposer(metamodels);
		EPackage finalMetamodel = composer.compose();

		// 4. Get the picture
		String id = properties.getProperty(COMPOSER_ID);
		if(id == null) throw new ServletException("ID for composer not found in properties");
		
		List<EObject> toDraw= new ArrayList<EObject>();
		toDraw.add(finalMetamodel);	
		File resultPath = drawModel(toDraw, id);		
		String resultImage = encodeToString(resultPath);
		resultPath.delete();
		
		// 4. Write the response
		PrintWriter out = response.getWriter();
		out.print(resultImage);
	}
}