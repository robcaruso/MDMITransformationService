/*******************************************************************************
 * Copyright (c) 2019 seanmuir.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     seanmuir - initial API and implementation
 *
 *******************************************************************************/
package org.mdmi.rt.service.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.commons.text.StringEscapeUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.mdmi.MessageModel;
import org.mdmi.core.MdmiMessage;
import org.mdmi.core.engine.postprocessors.IPostProcessor;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.IParserErrorHandler;
import ca.uhn.fhir.parser.json.JsonLikeValue.ScalarType;
import ca.uhn.fhir.parser.json.JsonLikeValue.ValueType;

/**
 * @author seanmuir
 *
 */

public class FHIRR4PostProcessorJson implements IPostProcessor {

	/*
	 * (non-Javadoc)
	 *
	 * @see org.mdmi.core.engine.postprocessors.IPostProcessor#canProcess(org.mdmi.MessageModel)
	 */
	@Override
	public boolean canProcess(MessageModel messageModel) {
		if ("FHIRR4JSON".equals(messageModel.getGroup().getName()) ||
				"IPSFHIRJSON".equals(messageModel.getGroup().getName()) ||
				"CCDAonFHIRJSON".equals(messageModel.getGroup().getName())) {
			return true;
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.mdmi.core.engine.postprocessors.IPostProcessor#getName()
	 */
	@Override
	public String getName() {
		return "FHIRR4PostProcessor";
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.mdmi.core.engine.postprocessors.IPostProcessor#processMessage(org.mdmi.MessageModel, org.mdmi.core.MdmiMessage)
	 */
	@Override
	public void processMessage(MessageModel messageModel, MdmiMessage mdmiMessage) {
		FhirContext ctx = FhirContext.forR4();
		if (ctx != null) {
			IParser parse = ctx.newXmlParser();
			IParserErrorHandler aaa = new IParserErrorHandler() {

				@Override
				public void containedResourceWithNoId(IParseLocation arg0) {
					// TODO Auto-generated method stub

				}

				@Override
				public void incorrectJsonType(IParseLocation arg0, String arg1, ValueType arg2, ScalarType arg3,
						ValueType arg4, ScalarType arg5) {
					// TODO Auto-generated method stub

				}

				@Override
				public void invalidValue(IParseLocation arg0, String arg1, String arg2) {
					// TODO Auto-generated method stub

				}

				@Override
				public void missingRequiredElement(IParseLocation arg0, String arg1) {
					// TODO Auto-generated method stub

				}

				@Override
				public void unexpectedRepeatingElement(IParseLocation arg0, String arg1) {
					// TODO Auto-generated method stub

				}

				@Override
				public void unknownAttribute(IParseLocation arg0, String arg1) {
					// TODO Auto-generated method stub

				}

				@Override
				public void unknownElement(IParseLocation arg0, String arg1) {
					// TODO Auto-generated method stub

				}

				@Override
				public void unknownReference(IParseLocation arg0, String arg1) {
					// TODO Auto-generated method stub

				}
			};
			parse.setParserErrorHandler(aaa);

			// Bundle bundle = parse.parseResource(Bundle.class, content);

			// // System.out.println(ctx.newJsonParser().encodeResourceToString(bundle));
			// Bundle bundle = parse.parseResource(Bundle.class, mdmiMessage.getDataAsString());
			// Bundle dedupBundle = deduplicate(bundle);
			// mdmiMessage.setData(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(dedupBundle));

			HashMap<String, String> referenceMappings = new HashMap<String, String>();
			Bundle bundle = parse.parseResource(Bundle.class, mdmiMessage.getDataAsString());
			Bundle dedupBundle = deduplicate(bundle);
			for (BundleEntryComponent bundleEntry : dedupBundle.getEntry()) {
				UUID uuid = UUID.randomUUID();
				bundleEntry.setFullUrl("urn:uuid:" + uuid);
				String k = bundleEntry.getResource().getId();
				if (k != null) {
					referenceMappings.put(k, "urn:uuid:" + uuid);
				}
				bundleEntry.getRequest().setUrl(bundleEntry.getResource().getResourceType().name());
				HTTPVerb post = null;
				bundleEntry.getRequest().setMethod(post.POST);
			}

			String result = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(dedupBundle);
			System.out.println(result);
			JSONParser parser = new JSONParser();

			try {
				Object obj = parser.parse(result);
				JSONObject jsonObject = (JSONObject) obj;
				walk(jsonObject, referenceMappings);
				mdmiMessage.setData(StringEscapeUtils.unescapeJson(jsonObject.toJSONString()));
				return;

			} catch (ParseException e) {
				e.printStackTrace();
			}

			mdmiMessage.setData(result);
		}
	}

	private void walk(JSONObject jsonObject, HashMap<String, String> referenceMappings) {
		for (Iterator iterator = jsonObject.keySet().iterator(); iterator.hasNext();) {
			String key = (String) iterator.next();
			if (key.equals("reference")) {
				// System.out.println(key + " : " + jsonObject.get(key));
				if (referenceMappings.containsKey(jsonObject.get(key))) {
					jsonObject.replace(key, referenceMappings.get(jsonObject.get(key)));
				}

			}
			if (jsonObject.get(key) instanceof JSONObject) {
				walk((JSONObject) jsonObject.get(key), referenceMappings);
			}
			if (jsonObject.get(key) instanceof JSONArray) {
				JSONArray array = (JSONArray) jsonObject.get(key);
				Consumer walkit = new Consumer() {
					@Override
					public void accept(Object t) {
						// System.out.println(t);
						if (t instanceof JSONObject) {
							walk((JSONObject) t, referenceMappings);
						}

					}
				};
				array.forEach(walkit);
			}
		}

	}

	private Bundle deduplicate(Bundle bundle) {
		HashMap<String, String> map = new HashMap<String, String>();
		ArrayList<BundleEntryComponent> removelist = new ArrayList<>();
		for (BundleEntryComponent bundleEntry : bundle.getEntry()) {
			if (bundleEntry.getResource().fhirType().equals("Practitioner")) {
				Practitioner practitioner = (Practitioner) bundleEntry.getResource();
				for (Identifier id : practitioner.getIdentifier()) {
					String sid = id.getSystem() + "::" + id.getValue();
					if (!map.containsKey(sid)) {
						map.put(sid, "");
					} else {
						removelist.add(bundleEntry);
					}
				}
			} else if (bundleEntry.getResource().fhirType().equals("Organization")) {
				Organization organization = (Organization) bundleEntry.getResource();
				for (Identifier id : organization.getIdentifier()) {
					String sid = id.getSystem() + "::" + id.getValue();
					if (!map.containsKey(sid)) {
						map.put(sid, "");
					} else {
						removelist.add(bundleEntry);
					}
				}
			} else if (bundleEntry.getResource().fhirType().equals("Medication")) {
				Medication medication = (Medication) bundleEntry.getResource();
				for (Identifier id : medication.getIdentifier()) {
					String sid = id.getSystem() + "::" + id.getValue();
					if (!map.containsKey(sid)) {
						map.put(sid, "");
					} else {
						removelist.add(bundleEntry);
					}
				}
			}
		}
		bundle.getEntry().removeAll(removelist);
		return bundle;
	}
}
