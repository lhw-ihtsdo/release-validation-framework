package org.ihtsdo.rvf.rest.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.commons.io.FileUtils;
import org.ihtsdo.rvf.core.messaging.ValidationQueueManager;
import org.ihtsdo.rvf.core.service.AssertionService;
import org.ihtsdo.rvf.core.data.model.AssertionGroup;
import org.ihtsdo.rvf.core.service.ValidationRunner;
import org.ihtsdo.rvf.core.service.config.ValidationRunConfig;
import org.ihtsdo.rvf.core.service.structure.validation.StructuralTestRunner;
import org.ihtsdo.rvf.core.service.structure.validation.TestReportable;
import org.ihtsdo.rvf.core.service.structure.listing.ManifestFile;
import org.ihtsdo.rvf.core.service.structure.resource.ResourceProvider;
import org.ihtsdo.rvf.core.service.structure.resource.TextFileResourceProvider;
import org.ihtsdo.rvf.core.service.structure.resource.ZipFileResourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import springfox.documentation.annotations.ApiIgnore;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.*;

/**
 * The controller that handles uploaded files for the validation to run
 */
@RestController
@Api(tags = "Validate", description ="-")
public class TestUploadFileController {

	private static final String ENABLE_MRCM_VALIDATION = "enableMRCMValidation";

	private static final String ENABLE_TRACEABILITY_VALIDATION = "enableTraceabilityValidation";
	private static final String ENABLE_CHANGE_NOT_AT_TASK_LEVEL_VALIDATION = "enableChangeNotAtTaskLevelValidation";

	private static final String DEFAULT_MODULE_ID = "defaultModuleId";

	private static final String INCLUDED_MODULES = "includedModules";

	private static final String RELEASE_AS_AN_EDITION = "releaseAsAnEdition";

	private static final String EFFECTIVE_TIME = "effectiveTime";

	private static final String ENABLE_DROOLS = "enableDrools";

	private static final String STORAGE_LOCATION = "storageLocation";

	private static final String FAILURE_EXPORT_MAX = "failureExportMax";

	private static final String RUN_ID = "runId";

	private static final String DEPENDENCY_RELEASE = "dependencyRelease";

	private static final String PREVIOUS_DEPENDENCY_EFFECTIVE_TIME = "previousDependencyEffectiveTime";

	private static final String BRANCH_PATH = "branchPath";

	private static final String EXCLUDED_REFSET_DESCRIPTOR_MEMBERS = "excludedRefsetDescriptorMembers";

	private static final String PREVIOUS_RELEASE = "previousRelease";

	private static final String DROOLS_RULES_GROUPS = "droolsRulesGroups";

	private static final String GROUPS = "groups";

	private static final String MANIFEST = "manifest";

	private static final String WRITE_SUCCESSES = "writeSuccesses";

	private static final String RF2_DELTA_ONLY = "rf2DeltaOnly";

	private static final String RESPONSE_QUEUE = "responseQueue";

	private static final String USER_NAME = "username";

	private static final String AUTH_TOKEN = "authenticationToken";

	private static final String ZIP = ".zip";

	private static final Logger LOGGER = LoggerFactory.getLogger(TestUploadFileController.class);

	@Autowired
	private StructuralTestRunner structureTestRunner;
	@Autowired
	private AssertionService assertionService;
	@Autowired
	private ValidationQueueManager queueManager;

	@RequestMapping(value = "/test-file", method = RequestMethod.POST)
	@ResponseBody
	@ApiOperation(value = "Structure tests", notes = "Uploaded files should be in RF2 format. Service can accept zip file or txt file. ")
	@ApiIgnore
	public ResponseEntity uploadTestPackage(
			@RequestParam(value = "file") final MultipartFile file,
			@RequestParam(value = WRITE_SUCCESSES, required = false, defaultValue = "false") final boolean writeSucceses,
			@RequestParam(value = MANIFEST, required = false) final MultipartFile manifestFile,
			final HttpServletResponse response) throws IOException {
		// load the filename
		final String filename = file.getOriginalFilename();
		// must be a zip
		if (filename.endsWith(ZIP)) {
			return uploadPostTestPackage(file, writeSucceses, manifestFile, response);
		} else if (filename.endsWith(".txt")) {
			return uploadPreTestPackage(file, writeSucceses, response);
		} else {
			throw new IllegalArgumentException("File should be pre or post and either a .txt or .zip is expected");
		}
	}

	@RequestMapping(value = "/test-post", method = RequestMethod.POST)
	@ResponseBody
	@ApiOperation(position = 2, value = "Structure tests for RF2 release files", notes = "Structure tests for RF2 release files in zip file format. The manifest file is optional.")
	@ApiIgnore
	public ResponseEntity uploadPostTestPackage(
			@ApiParam(value="RF2 release file in zip format")@RequestParam(value = "file") final MultipartFile file,
			@ApiParam(required = false, value = "Defaults to false when not provided") @RequestParam(value = WRITE_SUCCESSES, required = false) final boolean writeSucceses,
			@ApiParam(required = false, value = "manifest.xml file") @RequestParam(value = MANIFEST, required = false) final MultipartFile manifestFile,
			final HttpServletResponse response) throws IOException {
		// load the filename
		final String filename = file.getOriginalFilename();
		if (!filename.endsWith(ZIP)) {
			throw new IllegalArgumentException("Post condition test package has to be zipped up");
		}
		File tempFile = null;
		File tempManifestFile = null;
		try {
			tempFile = File.createTempFile(filename, ZIP);
			// set up the response in order to strean directly to the response
			response.setContentType("text/csv;charset=utf-8");
			response.setHeader("Content-Disposition",
					"attachment; filename=\"report_" + filename + "_" + new Date() + "\"");
			try (PrintWriter writer = response.getWriter()) {
				// must be a zip
				file.transferTo(tempFile);
				final ResourceProvider resourceManager = new ZipFileResourceProvider(tempFile);
				TestReportable report;

				if (manifestFile == null) {
					report = structureTestRunner.execute(resourceManager, writer, writeSucceses);
				} else {
					final String originalFilename = manifestFile.getOriginalFilename();
					tempManifestFile = File.createTempFile(originalFilename, ".xml");
					manifestFile.transferTo(tempManifestFile);
					final ManifestFile mf = new ManifestFile(tempManifestFile);
					report = structureTestRunner.execute(resourceManager, null, writer, writeSucceses, mf);
				}
				// store the report to disk for now with a timestamp
				if (report.getNumErrors() > 0) {
					LOGGER.error("No Errors expected but got " + report.getNumErrors() + " errors");
				}
			}
		} finally {
			if (tempFile != null) {
				tempFile.delete();
			}
			if (tempManifestFile != null) {
				tempManifestFile.delete();
			}
		}
		return new ResponseEntity<>(HttpStatus.OK);
	}

	@RequestMapping(value = "/run-post", method = RequestMethod.POST)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	@ApiOperation(value = "Run validations for a RF2 release file package.", notes = "It runs structure tests and assertion validations "
			+ "specified by the assertion groups. You can specify mutilple assertion group names separated by a comma. e.g common-authoring,int-authoring"
			+ "To run validations for an extension package, you need to select the zip file, use the first-time-common-edition assertion group, and specify the dependent international release version.")
	public ResponseEntity<Map<String, String>> runPostTestPackage(
			@ApiParam(value = "RF2 release package in zip file") @RequestParam(value = "file") final MultipartFile file,
			@ApiParam(value = "True if the test file contains RF2 delta files only. Defaults to false.") @RequestParam(value = RF2_DELTA_ONLY, required = false, defaultValue = "false") final boolean isRf2DeltaOnly,
			@ApiParam(value = "Default to false to reduce the size of report file") @RequestParam(value = WRITE_SUCCESSES, required = false, defaultValue = "false") final boolean writeSucceses,
			@ApiParam(value = "manifest.xml file(optional)") @RequestParam(value = MANIFEST, required = false) final MultipartFile manifestFile,
			@ApiParam(value = "Assertion group names separated by a comma.") @RequestParam(value = GROUPS) final List<String> groupsList,
			@ApiParam(value = "Drools rules group names") @RequestParam(value = DROOLS_RULES_GROUPS, required = false) final List<String> droolsRulesGroupsList,
			@ApiParam(value = "Required for non-first time international release testing") @RequestParam(value = PREVIOUS_RELEASE, required = false) final String previousRelease,
			@ApiParam(value = "Required for extension release testing") @RequestParam(value = DEPENDENCY_RELEASE, required = false) final String extensionDependency,
			@ApiParam(value = "Required for extension release testing") @RequestParam(value = PREVIOUS_DEPENDENCY_EFFECTIVE_TIME, required = false) final String previousDependencyEffectiveTime,
			@ApiParam(value = "Unique number e.g Timestamp") @RequestParam(value = RUN_ID) final Long runId,
			@ApiParam(value = "Defaults to 10 when not set", defaultValue = "10") @RequestParam(value = FAILURE_EXPORT_MAX, required = false, defaultValue = "10") final Integer exportMax,
			@ApiParam(value = "The sub folder for validation reports") @RequestParam(value = STORAGE_LOCATION) final String storageLocation,
			@ApiParam(value = "Defaults to false") @RequestParam(value = ENABLE_DROOLS, required = false) final boolean enableDrools,
			@ApiParam(value = "Effective time, optionally used in Drools validation, required if Jira creation flag is true") @RequestParam(value = EFFECTIVE_TIME, required = false) final String effectiveTime,
			@ApiParam(value = "If release package file is an MS edition, should set to true. Defaults to false") @RequestParam(value = RELEASE_AS_AN_EDITION, required = false) final boolean releaseAsAnEdition,
			@ApiParam(value = "Default module ID of components in the MS extension") @RequestParam(value = DEFAULT_MODULE_ID, required = false) final String defaultModuleId,
			@ApiParam(value = "Module IDs of components in the MS extension. Used for filtering results in Drools validation. Values are separated by comma") @RequestParam(value = INCLUDED_MODULES, required = false) final String includedModules,
			@ApiParam(value = "Defaults to false.") @RequestParam(value = ENABLE_MRCM_VALIDATION, required = false) final boolean enableMrcmValidation,
			@ApiParam(value = "Enable traceability validation.", defaultValue = "false") @RequestParam(value = ENABLE_TRACEABILITY_VALIDATION, required = false) final boolean enableTraceabilityValidation,
			@ApiParam(value = "Enable change not at task level validation. This parameter needs to be combined together with enableTraceabilityValidation.", defaultValue = "false") @RequestParam(value = ENABLE_CHANGE_NOT_AT_TASK_LEVEL_VALIDATION, required = false) final boolean enableChangeNotAtTaskLevelValidation,
			@ApiParam(value = "Terminology Server content branch path, used for traceability check.") @RequestParam(value = BRANCH_PATH, required = false) final String branchPath,
			@ApiParam(value = "Refset Descriptor members are being excluded, used for traceability check.") @RequestParam(value = EXCLUDED_REFSET_DESCRIPTOR_MEMBERS, required = false) final String excludedRefsetDescriptorMembers,
			@ApiParam(value = "Base timestamp of content branch, used for traceability rebase changes summary report.") @RequestParam(required = false) final Long contentBaseTimestamp,
			@ApiParam(value = "Head timestamp of content branch, used for stale state detection.") @RequestParam(required = false) final Long contentHeadTimestamp,
			@ApiParam(value = "Name of the response queue.") @RequestParam(value = RESPONSE_QUEUE, required = false) final String responseQueue,
			@ApiParam(value = "User name.") @RequestParam(value = USER_NAME, required = false) final String username,
			@ApiParam(value = "Authentication token.") @RequestParam(value = AUTH_TOKEN, required = false) final String authenticationToken,
			UriComponentsBuilder uriComponentsBuilder
			) throws IOException {

		ValidationRunConfig vrConfig = new ValidationRunConfig();
		String urlPrefix = URI.create(uriComponentsBuilder.toUriString()).toURL().toString();
		vrConfig.addFile(file).addRF2DeltaOnly(isRf2DeltaOnly)
				.addWriteSucceses(writeSucceses).addGroupsList(groupsList).addDroolsRulesGroupList(droolsRulesGroupsList)
				.addManifestFile(manifestFile)
				.addPreviousRelease(previousRelease)
				.addDependencyRelease(extensionDependency)
				.addPreviousDependencyEffectiveTime(previousDependencyEffectiveTime)
				.addRunId(runId).addStorageLocation(storageLocation)
				.addFailureExportMax(exportMax)
				.addProspectiveFilesInS3(false)
				.setEnableDrools(enableDrools)
				.setEffectiveTime(effectiveTime)
				.setReleaseAsAnEdition(releaseAsAnEdition)
				.setDefaultModuleId(defaultModuleId)
				.setIncludedModules(includedModules)
				.addUrl(urlPrefix)
				.setEnableMRCMValidation(enableMrcmValidation)
				.setEnableTraceabilityValidation(enableTraceabilityValidation)
				.setEnableChangeNotAtTaskLevelValidation(enableChangeNotAtTaskLevelValidation)
				.setBranchPath(branchPath)
				.setExcludedRefsetDescriptorMembers(excludedRefsetDescriptorMembers)
				.setContentHeadTimestamp(contentHeadTimestamp)
				.setContentBaseTimestamp(contentBaseTimestamp)
				.addResponseQueue(responseQueue)
				.setUsername(username)
				.setAuthenticationToken(authenticationToken);

		// Before we start running, ensure that we've made our mark in the storage location
		// Init will fail if we can't write the "running" state to storage
		final Map<String, String> responseMap = new HashMap<>();
		if (isAssertionGroupsValid(vrConfig.getGroupsList(), responseMap)) {
			// Queue incoming validation request
			queueManager.queueValidationRequest(vrConfig, responseMap);
			URI uri = createResultURI(runId, storageLocation, uriComponentsBuilder);
			LOGGER.info("RVF result url:{}", uri.toURL());
			return ResponseEntity.created(uri).body(responseMap);
		} else {
			return new ResponseEntity<>(HttpStatus.PRECONDITION_FAILED);
		}
	}

	@RequestMapping(value = "/run-post-via-s3", method = RequestMethod.POST)
	@ResponseBody
	@ResponseStatus(HttpStatus.OK)
	@ApiOperation(value = "Run validations for release files stored in AWS S3", notes = "This api is mainly used by the RVF autoscalling "
			+ "instances to validate release files stored in AWS S3.")
	public ResponseEntity<Map<String, String>> runPostTestPackageViaS3(
			@ApiParam(value = "S3 bucket name") @RequestParam(value = "bucketName") String bucketName,
			@ApiParam(value = "Release zip file path in S3") @RequestParam(value = "releaseFileS3Path") String releaseFileS3Path,
			@ApiParam(value = "True if the test file contains RF2 delta files only. Defaults to false.") @RequestParam(value = RF2_DELTA_ONLY, required = false, defaultValue = "false") final boolean isRf2DeltaOnly,
			@ApiParam(value = "Defaults to false to reduce the size of report file") @RequestParam(value = WRITE_SUCCESSES, required = false) final boolean writeSucceses,
			@ApiParam(value = "manifest.xml file path in AWS S3") @RequestParam(value = "manifestFileS3Path", required = false) final String manifestFileS3Path,
			@ApiParam(value = "Assertion group names") @RequestParam(value = GROUPS) final List<String> groupsList,
			@ApiParam(value = "Drools rules group names") @RequestParam(value = DROOLS_RULES_GROUPS, required = false) final List<String> droolsRulesGroupsList,
			@ApiParam(value = "Required for non-first time international release testing") @RequestParam(value = PREVIOUS_RELEASE, required = false) final String previousRelease,
			@ApiParam(value = "Required for extension release testing") @RequestParam(value = DEPENDENCY_RELEASE, required = false) final String extensionDependency,
			@ApiParam(value = "Required for extension release testing") @RequestParam(value = PREVIOUS_DEPENDENCY_EFFECTIVE_TIME, required = false) final String previousDependencyEffectiveTime,
			@ApiParam(value = "Unique run id e.g Timestamp") @RequestParam(value = RUN_ID) final Long runId,
			@ApiParam(value = "Defaults to 10 when not set", defaultValue = "10") @RequestParam(value = FAILURE_EXPORT_MAX, required = false, defaultValue = "10") final Integer exportMax,
			@ApiParam(value = "The sub folder for validation reports") @RequestParam(value = STORAGE_LOCATION) final String storageLocation,
			@ApiParam(value = "Defaults to false") @RequestParam(value = ENABLE_DROOLS, required = false) final boolean enableDrools,
			@ApiParam(value = "Effective time, optionally used in Drools validation, required if Jira creation flag is true") @RequestParam(value = EFFECTIVE_TIME, required = false) final String effectiveTime,
			@ApiParam(value = "Base timestamp of content branch, used for traceability rebase changes summary report.") @RequestParam(required = false) final Long contentBaseTimestamp,
			@ApiParam(value = "Head timestamp of content branch, used for stale state detection.") @RequestParam(required = false) final Long contentHeadTimestamp,
			@ApiParam(value = "If release package file is an MS edition, should set to true. Defaults to false") @RequestParam(value = RELEASE_AS_AN_EDITION, required = false) final boolean releaseAsAnEdition,
			@ApiParam(value = "Default module ID of components in the MS extension") @RequestParam(value = DEFAULT_MODULE_ID, required = false) final String defaultModuleId,
			@ApiParam(value = "Module IDs of components in the MS extension. Used for filtering results in Drools validation. Values are separated by comma") @RequestParam(value = INCLUDED_MODULES, required = false) final String includedModules,
			@ApiParam(value = "Defaults to false.") @RequestParam(value = ENABLE_MRCM_VALIDATION, required = false) final boolean enableMrcmValidation,
			@ApiParam(value = "Enable traceability validation.", defaultValue = "false") @RequestParam(value = ENABLE_TRACEABILITY_VALIDATION, required = false) final boolean enableTraceabilityValidation,
			@ApiParam(value = "Enable change not at task level validation. This parameter needs to be combined together with enableTraceabilityValidation.", defaultValue = "false") @RequestParam(value = ENABLE_CHANGE_NOT_AT_TASK_LEVEL_VALIDATION, required = false) final boolean enableChangeNotAtTaskLevelValidation,
			@ApiParam(value = "Terminology Server content branch path, used for traceability validation.") @RequestParam(value = BRANCH_PATH, required = false) final String branchPath,
			@ApiParam(value = "Refset Descriptor members are being excluded, used for traceability check.") @RequestParam(value = EXCLUDED_REFSET_DESCRIPTOR_MEMBERS, required = false) final String excludedRefsetDescriptorMembers,
			@ApiParam(value = "Name of the response queue.") @RequestParam(value = RESPONSE_QUEUE, required = false) final String responseQueue,
			@ApiParam(value = "User name.") @RequestParam(value = USER_NAME, required = false) final String username,
			@ApiParam(value = "Authentication token.") @RequestParam(value = AUTH_TOKEN, required = false) final String authenticationToken,
			UriComponentsBuilder uriComponentsBuilder) throws IOException {
		ValidationRunConfig vrConfig = new ValidationRunConfig();
		String urlPrefix = URI.create(uriComponentsBuilder.toUriString()).toURL().toString();
		vrConfig.addBucketName(bucketName)
				.addProspectiveFileFullPath(releaseFileS3Path)
				.addRF2DeltaOnly(isRf2DeltaOnly)
				.addWriteSucceses(writeSucceses)
				.addGroupsList(groupsList)
				.addDroolsRulesGroupList(droolsRulesGroupsList)
				.addManifestFileFullPath(manifestFileS3Path)
				.addPreviousRelease(previousRelease)
				.addDependencyRelease(extensionDependency)
				.addPreviousDependencyEffectiveTime(previousDependencyEffectiveTime)
				.addRunId(runId)
				.addStorageLocation(storageLocation)
				.addFailureExportMax(exportMax)
				.addUrl(urlPrefix)
				.addProspectiveFilesInS3(true)
				.setEnableDrools(enableDrools)
				.setEffectiveTime(effectiveTime)
				.setReleaseAsAnEdition(releaseAsAnEdition)
				.setDefaultModuleId(defaultModuleId)
				.setIncludedModules(includedModules)
				.setEnableMRCMValidation(enableMrcmValidation)
				.setEnableTraceabilityValidation(enableTraceabilityValidation)
				.setEnableChangeNotAtTaskLevelValidation(enableChangeNotAtTaskLevelValidation)
				.setBranchPath(branchPath)
				.setExcludedRefsetDescriptorMembers(excludedRefsetDescriptorMembers)
				.setContentHeadTimestamp(contentHeadTimestamp)
				.setContentBaseTimestamp(contentBaseTimestamp)
				.addResponseQueue(responseQueue)
				.setUsername(username)
				.setAuthenticationToken(authenticationToken);

		// Before we start running, ensure that we've made our mark in the storage location
		// Init will fail if we can't write the "running" state to storage
		final Map<String, String> responseMap = new HashMap<>();
		if (isAssertionGroupsValid(vrConfig.getGroupsList(), responseMap)) {
			// Queue incoming validation request
			queueManager.queueValidationRequest(vrConfig, responseMap);
			URI uri = createResultURI(runId, storageLocation, uriComponentsBuilder);
			LOGGER.info("RVF result url: {}", uri.toURL());
			if (responseMap.containsKey("failureMessage")) {
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
			}
			return ResponseEntity.created(uri).body(responseMap);
		} else {
			return new ResponseEntity<>(HttpStatus.PRECONDITION_FAILED);
		}
	}
		
	private boolean isAssertionGroupsValid(List<String> validationGroups,
			Map<String, String> responseMap) {
		// check assertion groups

		if (ValidationRunner.EMPTY_TEST_ASSERTION_GROUPS.equals(validationGroups)) {
			return true;
		}

		List<AssertionGroup> groups = assertionService.getAssertionGroupsByNames(validationGroups);
		if (groups.size() != validationGroups.size()) {
			final List<String> found = new ArrayList<>();
			for (final AssertionGroup group : groups) {
				found.add(group.getName());
			}
			final String groupNotFoundMsg = String.format("Assertion groups requested: %s but found in RVF: %s", validationGroups, found);
			responseMap.put("failureMessage", groupNotFoundMsg);
			LOGGER.warn("Invalid assertion groups requested." + groupNotFoundMsg);
			return false;
		}
		return true;
	}

	@RequestMapping(value = "/test-pre", method = RequestMethod.POST)
	@ResponseBody
	@ApiOperation(value = "Structure testing for release input files.", notes = "This API is for structure testing the RF2 text "
			+ "files used as inputs for release builds. These RF2 files are prefixed with rel2 e.g rel2_Concept_Delta_INT_20160731.txt")
	@ApiIgnore
	public ResponseEntity uploadPreTestPackage(
			@ApiParam(value = "RF2 input file prefixed with rel2", required = true) @RequestParam(value = "file") final MultipartFile file,
			@RequestParam(value = WRITE_SUCCESSES, required = false) final boolean writeSucceses,
			final HttpServletResponse response) throws IOException {
		// load the filename
		final String filename = file.getOriginalFilename();

		if (!filename.startsWith("rel")) {
			LOGGER.error("Not a valid pre condition file " + filename);
			return null;
		}

		final File tempFile = File.createTempFile(filename, ".txt");
		try {
			if (!filename.endsWith(".txt")) {
				throw new IllegalArgumentException(
						"Pre condition file should always be a .txt file");
			}
			response.setContentType("text/csv;charset=utf-8");
			response.setHeader("Content-Disposition",
					"attachment; filename=\"report_" + filename + "_" + new Date() + "\"");

			try (PrintWriter writer = response.getWriter()) {
				file.transferTo(tempFile);
				final ResourceProvider resourceManager = new TextFileResourceProvider(
						tempFile, filename);
				final TestReportable report = structureTestRunner.execute(resourceManager, null, writer, writeSucceses, null);
				// store the report to disk for now with a timestamp
				if (report.getNumErrors() > 0) {
					LOGGER.error("No Errors expected but got " + report.getNumErrors() + " errors");
				}
			}
			return null;
		} finally {
			FileUtils.deleteQuietly(tempFile);
		}
	}
	
	private URI createResultURI(final Long runId, final String storageLocation, final UriComponentsBuilder uriComponentsBuilder) {
		return uriComponentsBuilder.path("/result/{run_id}").query("storageLocation={storage_location}")
				.buildAndExpand(runId, storageLocation).toUri();
	}
}
