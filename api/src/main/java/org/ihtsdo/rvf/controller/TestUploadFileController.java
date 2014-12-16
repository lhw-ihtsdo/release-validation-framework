package org.ihtsdo.rvf.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.ihtsdo.rvf.entity.Assertion;
import org.ihtsdo.rvf.entity.AssertionGroup;
import org.ihtsdo.rvf.execution.service.AssertionExecutionService;
import org.ihtsdo.rvf.execution.service.ReleaseDataManager;
import org.ihtsdo.rvf.execution.service.util.TestRunItem;
import org.ihtsdo.rvf.helper.MissingEntityException;
import org.ihtsdo.rvf.service.AssertionService;
import org.ihtsdo.rvf.service.EntityService;
import org.ihtsdo.rvf.validation.TestReportable;
import org.ihtsdo.rvf.validation.ValidationTestRunner;
import org.ihtsdo.rvf.validation.model.ManifestFile;
import org.ihtsdo.rvf.validation.resource.ResourceManager;
import org.ihtsdo.rvf.validation.resource.TextFileResourceProvider;
import org.ihtsdo.rvf.validation.resource.ZipFileResourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.*;

/**
 * The controller that handles uploaded files for the validation to run
 */
@Controller
public class TestUploadFileController {

	private static final Logger LOGGER = LoggerFactory.getLogger(TestUploadFileController.class);

	@Autowired
	private ValidationTestRunner validationRunner;
	@Autowired
	private AssertionService assertionService;
    @Autowired
	private EntityService entityService;
	@Autowired
	private AssertionExecutionService assertionExecutionService;
	@Autowired
	ReleaseDataManager releaseDataManager;
	ObjectMapper objectMapper = new ObjectMapper();

	@RequestMapping(value = "/test-file", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity uploadTestPackage(@RequestParam(value = "file") MultipartFile file,
			@RequestParam(value = "writeSuccesses", required = false) boolean writeSucceses,
			@RequestParam(value = "manifest", required = false) MultipartFile manifestFile,
			HttpServletResponse response) throws IOException {
		// load the filename
		String filename = file.getOriginalFilename();
		// must be a zip
		if (filename.endsWith(".zip")) {
			return uploadPostTestPackage(file, writeSucceses, manifestFile, response);

		} else if (filename.endsWith(".txt")) {
			return uploadPreTestPackage(file, writeSucceses, response);
		} else {
			throw new IllegalArgumentException("File should be pre or post and either a .txt or .zip is expected");
		}
	}

	@RequestMapping(value = "/test-post", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity uploadPostTestPackage(@RequestParam(value = "file") MultipartFile file,
			@RequestParam(value = "writeSuccesses", required = false) boolean writeSucceses,
			@RequestParam(value = "manifest", required = false) MultipartFile manifestFile,
			HttpServletResponse response) throws IOException {
		// load the filename
		String filename = file.getOriginalFilename();

		final File tempFile = File.createTempFile(filename, ".zip");
		tempFile.deleteOnExit();
		if (!filename.endsWith(".zip")) {
			throw new IllegalArgumentException("Post condition test package has to be zipped up");
		}

		// set up the response in order to strean directly to the response
		response.setContentType("text/csv;charset=utf-8");
		response.setHeader("Content-Disposition", "attachment; filename=\"report_" + filename + "_" + new Date() + "\"");
		PrintWriter writer = response.getWriter();

		// must be a zip
		copyUploadToDisk(file, tempFile);
		ResourceManager resourceManager = new ZipFileResourceProvider(tempFile);

		TestReportable report;

		if (manifestFile == null) {
			report = validationRunner.execute(resourceManager, writer, writeSucceses);
		} else {
			String originalFilename = manifestFile.getOriginalFilename();
			final File tempManifestFile = File.createTempFile(originalFilename, ".xml");
			tempManifestFile.deleteOnExit();
			copyUploadToDisk(manifestFile, tempManifestFile);

			ManifestFile mf = new ManifestFile(tempManifestFile);
			report = validationRunner.execute(resourceManager, writer, writeSucceses, mf);
		}

		// store the report to disk for now with a timestamp
		if (report.getNumErrors() > 0) {
			LOGGER.error("No Errors expected but got " + report.getNumErrors() + " errors");
		}

		return null;
	}

	@RequestMapping(value = "/run-post", method = RequestMethod.POST)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	public ResponseEntity runPostTestPackage(
			@RequestParam(value = "file") MultipartFile file,
			@RequestParam(value = "writeSuccesses", required = false) boolean writeSucceses,
			@RequestParam(value = "manifest", required = false) MultipartFile manifestFile,
			@RequestParam(value = "groups") List<String> groupsList,
			@RequestParam(value = "prospectiveReleaseVersion") String prospectiveReleaseVersion,
			@RequestParam(value = "previousReleaseVersion") String previousReleaseVersion,
			@RequestParam(value = "runId") Long runId,
            HttpServletRequest request) throws IOException {

        // generate url from request so we can display in response
        String requestUrl = String.valueOf(request.getRequestURL());
        String urlPrefix = requestUrl.substring(0, requestUrl.lastIndexOf(request.getPathInfo()));

        Map<String , Object> responseMap = new HashMap<>();
		// convert groups which is passed as string to assertion groups
		List<AssertionGroup> groups = getAssertionGroups(groupsList);

		// load the filename
		String filename = file.getOriginalFilename();
		System.out.println("filename = " + filename);

		final File tempFile = File.createTempFile(filename, ".zip");
		tempFile.deleteOnExit();
		if (!filename.endsWith(".zip")) {
			responseMap.put("type", "pre");
			responseMap.put("assertionsFailed", 0);
			responseMap.put("report", "Post condition test package has to be zipped up");
			return new ResponseEntity<>(responseMap, HttpStatus.OK);
		}

		// set up the response in order to strean directly to the response
		File reportFile = new File(validationRunner.getReportDataFolder(), "manifest_validation_"+runId+".txt");
		PrintWriter writer = new PrintWriter(reportFile);

		// must be a zip
		copyUploadToDisk(file, tempFile);
		ResourceManager resourceManager = new ZipFileResourceProvider(tempFile);

		TestReportable report;

		if (manifestFile == null) {
			report = validationRunner.execute(resourceManager, writer, writeSucceses);
		} else {
			String originalFilename = manifestFile.getOriginalFilename();
			final File tempManifestFile = File.createTempFile(originalFilename, ".xml");
			tempManifestFile.deleteOnExit();
			copyUploadToDisk(manifestFile, tempManifestFile);

			ManifestFile mf = new ManifestFile(tempManifestFile);
			report = validationRunner.execute(resourceManager, writer, writeSucceses, mf);
		}

		// verify if manifest is valid
		if(report.getNumErrors() > 0){

			LOGGER.error("No Errors expected but got " + report.getNumErrors() + " errors");
			responseMap.put("type", "pre");
			responseMap.put("assertionsRun", report.getNumTestRuns());
			responseMap.put("assertionsFailed", report.getNumErrors());
			LOGGER.info("reportPhysicalUrl : " + reportFile.getAbsolutePath());
            // pass file name without extension - we add this back when we retrieve using controller
            responseMap.put("reportUrl", urlPrefix+"/reports/"+ FilenameUtils.removeExtension(reportFile.getName()));

            System.out.println("report.getNumErrors()/report.getNumTestRuns() = " + report.getNumErrors() / report.getNumTestRuns());
            // bail out only if number of test failures exceeds threshold
            if(report.getNumErrors()/report.getNumTestRuns() > validationRunner.getFailureThreshold()){
                return new ResponseEntity<>(responseMap, HttpStatus.OK);
            }
        }

		/*
			If we are here, assume manifest is valid, so load data from file.
		  */
		if(!releaseDataManager.isKnownRelease(prospectiveReleaseVersion)){
			releaseDataManager.loadSnomedData(prospectiveReleaseVersion, true, tempFile);
		}

		if(!releaseDataManager.isKnownRelease(previousReleaseVersion)){
			// the previous published release must already be present in database, otherwise we throw an error!
            responseMap.put("type", "post");
            responseMap.put("failureMessage", "Please load release data first for version : " + previousReleaseVersion);
            LOGGER.info("reportPhysicalUrl : " + reportFile.getAbsolutePath());
            // pass file name without extension - we add this back when we retrieve using controller
            responseMap.put("reportUrl", urlPrefix+"/reports/"+ FilenameUtils.removeExtension(reportFile.getName()));
            return new ResponseEntity<>(responseMap, HttpStatus.OK);
        }

		Map<Assertion, Collection<TestRunItem>> map = new HashMap<>();
		int failedAssertionCount = 0;
		Set<Long> assertionIds = new HashSet<>();
		for(AssertionGroup group : groups)
		{
			for(Assertion assertion : assertionService.getAssertionsForGroup(group)){
				assertionIds.add(assertion.getId());
			}
		}
		for (Long id: assertionIds) {
			try
			{
				Assertion assertion = assertionService.find(id);
				List<TestRunItem> items = new ArrayList<>(assertionExecutionService.executeAssertion(assertion, runId,
						prospectiveReleaseVersion, previousReleaseVersion));
				// get only first since we have 1:1 correspondence between Assertion and Test
				if(items.size() == 1){
					TestRunItem runItem = items.get(0);
					if(runItem.isFailure()){
						failedAssertionCount++;
					}
				}
				map.put(assertion, items);
			}
			catch (MissingEntityException e) {
				failedAssertionCount++;
			}
		}

        responseMap.put("type", "post");
        responseMap.put("assertions", map);
		responseMap.put("assertionsRun", map.keySet().size());
		responseMap.put("assertionsFailed", failedAssertionCount);
        LOGGER.info("reportPhysicalUrl : " + reportFile.getAbsolutePath());
        // pass file name without extension - we add this back when we retrieve using controller
        responseMap.put("reportUrl", urlPrefix+"/reports/"+ FilenameUtils.removeExtension(reportFile.getName()));

        return new ResponseEntity<>(responseMap, HttpStatus.OK);
	}

	@RequestMapping(value = "/test-pre", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity uploadPreTestPackage(@RequestParam(value = "file") MultipartFile file,
			@RequestParam(value = "writeSuccesses", required = false) boolean writeSucceses,
			HttpServletResponse response) throws IOException {
		// load the filename
		String filename = file.getOriginalFilename();

		if (!filename.startsWith("rel")) {
			LOGGER.error("Not a valid pre condition file " + filename);
			return null;
		}

		final File tempFile = File.createTempFile(filename, ".txt");
		tempFile.deleteOnExit();
		if (!filename.endsWith(".txt")) {
			throw new IllegalArgumentException("Pre condition file should always be a .txt file");
		}

		response.setContentType("text/csv;charset=utf-8");
		response.setHeader("Content-Disposition", "attachment; filename=\"report_" + filename + "_" + new Date() + "\"");
		PrintWriter writer = response.getWriter();

		copyUploadToDisk(file, tempFile);

		ResourceManager resourceManager = new TextFileResourceProvider(tempFile, filename);
		TestReportable report = validationRunner.execute(resourceManager, writer, writeSucceses);

		// store the report to disk for now with a timestamp
		if (report.getNumErrors() > 0) {
			LOGGER.error("No Errors expected but got " + report.getNumErrors() + " errors");
		}


		return null;
	}

	@RequestMapping(value = "/reports/{id}", method = RequestMethod.GET)
	@ResponseBody
	public FileSystemResource getFile(@PathVariable String id) {
		return new FileSystemResource(new File(validationRunner.getReportDataFolder(), id+".txt"));
	}

	private void copyUploadToDisk(MultipartFile file, File tempFile) throws IOException {
		try (FileOutputStream out = new FileOutputStream(tempFile)) {
			InputStream inputStream = file.getInputStream();
			IOUtils.copy(inputStream, out);
		}
	}

	private List<AssertionGroup> getAssertionGroups(List<String> items){

		List<AssertionGroup> groups = new ArrayList<>();
		for(String item: items){
            try
            {
                if(item.matches("\\d+")){
                    // treat as group id and retrieve associated group
                    AssertionGroup group = (AssertionGroup) entityService.find(AssertionGroup.class, Long.valueOf(item));
                    if(group != null){
                        groups.add(group);
                    }
                }
                else{
                    groups.add(objectMapper.readValue(item, AssertionGroup.class));
                }
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}

		return groups;
	}
}
