package org.ihtsdo.rvf.controller;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

@Controller
@RequestMapping("/version")
@Api(position=6,value = "Release Versions")
public class VersionController {

	public static final String VERSION_FILE_PATH = "/opt/rvf-api/data/version.txt";

	private String versionString;

    @RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	@ApiOperation( value = "Get version of deployed RVF service",
		notes = "Get version of deployed RVF service" )
	public Map<String, String> getVersion(HttpServletRequest request) throws IOException {
		Map<String, String> entity = new HashMap<>();
		entity.put("package_version", getVersionString());
		return entity;//hypermediaGenerator.getEntityHypermedia(entity, true, request, new String[]{});
	}

	private String getVersionString() throws IOException {
		if (this.versionString == null) {
			String versionString = "";
			File file = new File(VERSION_FILE_PATH);
			if (file.isFile()) {
				try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
					versionString = bufferedReader.readLine();
				}
			} else {
				versionString = "Version information not found.";
			}
			this.versionString = versionString;
		}
		return versionString;
	}

}
