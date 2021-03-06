package io.dockstore.webservice.helpers;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.gson.Gson;

import io.dockstore.webservice.core.Container;
import io.dockstore.webservice.resources.ResourceUtilities;

/**
 * @author dyuen
 */
public class BitBucketSourceCodeRepo extends SourceCodeRepoInterface {
    private static final String BITBUCKET_API_URL = "https://bitbucket.org/api/1.0/";

    private static final Logger LOG = LoggerFactory.getLogger(BitBucketSourceCodeRepo.class);
    private final String gitUsername;
    private final HttpClient client;
    private final String bitbucketTokenContent;
    private final String gitRepository;

    public BitBucketSourceCodeRepo(String gitUsername, HttpClient client, String bitbucketTokenContent, String gitRepository) {
        this.client = client;
        this.bitbucketTokenContent = bitbucketTokenContent;
        this.gitUsername = gitUsername;
        this.gitRepository = gitRepository;
    }

    @Override
    public FileResponse readFile(String fileName, String reference) {
        if (fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }

        FileResponse fileResponse = new FileResponse();

        String content;
        String branch = null;

        if (reference == null) {
            String mainBranchUrl = BITBUCKET_API_URL + "repositories/" + gitUsername + '/' + gitRepository + "/main-branch";

            Optional<String> asString = ResourceUtilities.asString(mainBranchUrl, bitbucketTokenContent, client);
            LOG.info("RESOURCE CALL: {}", mainBranchUrl);
            if (asString.isPresent()) {
                String branchJson = asString.get();

                Gson gson = new Gson();
                Map<String, String> map = new HashMap<>();
                map = (Map<String, String>) gson.fromJson(branchJson, map.getClass());

                branch = map.get("name");

                if (branch == null) {
                    LOG.info("Could NOT find bitbucket default branch!");
                    return null;
                    // throw new CustomWebApplicationException(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                } else {
                    LOG.info("Default branch: {}", branch);
                }
            }
        } else {
            branch = reference;
        }

        String url = BITBUCKET_API_URL + "repositories/" + gitUsername + '/' + gitRepository + "/raw/" + branch + '/' + fileName;
        Optional<String> asString = ResourceUtilities.asString(url, bitbucketTokenContent, client);
        LOG.info("RESOURCE CALL: {}", url);
        if (asString.isPresent()) {
            LOG.info("FOUND: {}", fileName);
            content = asString.get();
        } else {
            LOG.info("Branch: {} has no {}", branch, fileName);
            return null;
        }

        if (content != null && !content.isEmpty()) {
            fileResponse.setContent(content);
        } else {
            return null;
        }

        return fileResponse;
    }

    @Override
    public Container findCWL(Container container) {
        String fileName = container.getDefaultCwlPath();
        if (fileName.startsWith("/")) {
            fileName = fileName.substring(1);
        }

        String giturl = container.getGitUrl();
        if (giturl != null && !giturl.isEmpty()) {

            Pattern p = Pattern.compile("git\\@bitbucket.org:(\\S+)/(\\S+)\\.git");
            Matcher m = p.matcher(giturl);
            LOG.info(giturl);
            if (!m.find()) {
                LOG.info("Namespace and/or repository name could not be found from container's giturl");
                return container;
            }

            String url = BITBUCKET_API_URL + "repositories/" + m.group(1) + '/' + m.group(2) + "/main-branch";
            Optional<String> asString = ResourceUtilities.asString(url, bitbucketTokenContent, client);
            LOG.info("RESOURCE CALL: {}", url);
            if (asString.isPresent()) {
                String branchJson = asString.get();

                Gson gson = new Gson();
                Map<String, String> map = new HashMap<>();
                map = (Map<String, String>) gson.fromJson(branchJson, map.getClass());

                String branch = map.get("name");

                if (branch == null) {
                    LOG.info("Could NOT find bitbucket default branch!");
                    return null;
                } else {
                    LOG.info("Default branch: {}", branch);
                }

                // String response = asString.get();
                //
                // Gson gson = new Gson();
                // Map<String, Object> branchMap = new HashMap<>();
                //
                // branchMap = (Map<String, Object>) gson.fromJson(response, branchMap.getClass());
                // Set<String> branches = branchMap.keySet();
                //
                // for (String branch : branches) {
                LOG.info("Checking {} branch for cwl file", branch);

                String content = "";

                url = BITBUCKET_API_URL + "repositories/" + m.group(1) + '/' + m.group(2) + "/raw/" + branch + '/' + fileName;
                asString = ResourceUtilities.asString(url, bitbucketTokenContent, client);
                LOG.info("RESOURCE CALL: {}", url);
                if (asString.isPresent()) {
                    LOG.info("CWL FOUND");
                    content = asString.get();
                } else {
                    LOG.info("Branch: {} has no {}", branch, fileName);
                }

                container = parseCWLContent(container, content);

                // if (container.getHasCollab()) {
                // break;
                // }
                // }

            }
        }

        return container;
    }

}
