/*
 * Copyright (C) 2015 Consonance
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.webservice.resources;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;

import io.dockstore.webservice.CustomWebApplicationException;
import io.dockstore.webservice.Helper;
import io.dockstore.webservice.core.Token;
import io.dockstore.webservice.core.TokenType;
import io.dockstore.webservice.core.User;
import io.dockstore.webservice.jdbi.TokenDAO;
import io.dockstore.webservice.jdbi.UserDAO;
import io.dropwizard.auth.Auth;
import io.dropwizard.auth.CachingAuthenticator;
import io.dropwizard.hibernate.UnitOfWork;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * The githubToken resource handles operations with tokens. Tokens are needed to talk with the quay.io and github APIs. In addition, they
 * will be needed to pull down docker containers that are requested by users.
 *
 * @author dyuen
 */
@Path("/auth/tokens")
@Api(value = "/auth/tokens", tags = "tokens")
@Produces(MediaType.APPLICATION_JSON)
public class TokenResource {
    private final TokenDAO tokenDAO;
    private final UserDAO userDAO;
    private static final String GIT_URL = "https://github.com/";
    private static final String QUAY_URL = "https://quay.io/api/v1/";
    private static final String BITBUCKET_URL = "https://bitbucket.org/";
    private final String githubClientID;
    private final String githubClientSecret;
    private final String bitbucketClientID;
    private final String bitbucketClientSecret;
    private final HttpClient client;

    private static final int MAX_ITERATIONS = 5;

    private static final Logger LOG = LoggerFactory.getLogger(TokenResource.class);
    private final CachingAuthenticator<String, Token> cachingAuthenticator;

    @SuppressWarnings("checkstyle:parameternumber")
    public TokenResource(TokenDAO tokenDAO, UserDAO enduserDAO, String githubClientID, String githubClientSecret, String bitbucketClientID,
            String bitbucketClientSecret, HttpClient client, CachingAuthenticator<String, Token> cachingAuthenticator) {
        this.tokenDAO = tokenDAO;
        userDAO = enduserDAO;
        this.githubClientID = githubClientID;
        this.githubClientSecret = githubClientSecret;
        this.bitbucketClientID = bitbucketClientID;
        this.bitbucketClientSecret = bitbucketClientSecret;
        this.client = client;
        this.cachingAuthenticator = cachingAuthenticator;
    }

    @GET
    @Timed
    @UnitOfWork
    @ApiOperation(value = "List all known tokens", notes = "List all tokens. Admin Only.", response = Token.class, responseContainer = "List")
    public List<Token> listTokens(@ApiParam(hidden = true) @Auth Token authToken) {
        User user = userDAO.findById(authToken.getUserId());
        Helper.checkUser(user);

        return tokenDAO.findAll();
    }

    @GET
    @Path("/{tokenId}")
    @Timed
    @UnitOfWork
    @ApiOperation(value = "Get a specific token by id", notes = "Get a specific token by id", response = Token.class)
    @ApiResponses({ @ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid ID supplied"),
            @ApiResponse(code = HttpStatus.SC_NOT_FOUND, message = "Token not found") })
    public Token listToken(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam("ID of token to return") @PathParam("tokenId") Long tokenId) {
        User user = userDAO.findById(authToken.getUserId());
        Token t = tokenDAO.findById(tokenId);
        Helper.checkUser(user, t.getUserId());

        return t;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/quay.io")
    @ApiOperation(value = "Add a new quay IO token", notes = "This is used as part of the OAuth 2 web flow. "
            + "Once a user has approved permissions for Collaboratory"
            + "Their browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addQuayToken(@ApiParam(hidden = true) @Auth Token authToken, @QueryParam("access_token") String accessToken) {
        if (accessToken.isEmpty()) {
            throw new CustomWebApplicationException("Please provide an access token.", HttpStatus.SC_BAD_REQUEST);
        }

        User user = userDAO.findById(authToken.getUserId());

        String url = QUAY_URL + "user/";
        Optional<String> asString = ResourceUtilities.asString(url, accessToken, client);

        String username = null;
        if (asString.isPresent()) {
            LOG.info("RESOURCE CALL: {}", url);

            String response = asString.get();
            Gson gson = new Gson();
            Map<String, String> map = new HashMap<>();
            map = (Map<String, String>) gson.fromJson(response, map.getClass());

            username = map.get("username");
            LOG.info("Username: {}", username);
        }

        if (user != null) {
            List<Token> tokens = tokenDAO.findQuayByUserId(user.getId());

            if (tokens.isEmpty()) {
                Token token = new Token();
                token.setTokenSource(TokenType.QUAY_IO.toString());
                token.setContent(accessToken);
                token.setUserId(user.getId());
                if (username != null) {
                    token.setUsername(username);
                } else {
                    LOG.info("Quay.io tokenusername is null, did not create token");
                    throw new CustomWebApplicationException("Username not found from resource call " + url, HttpStatus.SC_CONFLICT);
                }
                long create = tokenDAO.create(token);
                LOG.info("Quay token created for {}", user.getUsername());
                return tokenDAO.findById(create);
            } else {
                LOG.info("Quay token already exists for {}", user.getUsername());
                throw new CustomWebApplicationException("Quay token already exists for " + user.getUsername(), HttpStatus.SC_CONFLICT);
            }
        } else {
            LOG.info("Could not find user");
            throw new CustomWebApplicationException("User not found", HttpStatus.SC_CONFLICT);
        }
    }

    @DELETE
    @Path("/{tokenId}")
    @UnitOfWork
    @ApiOperation("Deletes a token")
    @ApiResponses(@ApiResponse(code = HttpStatus.SC_BAD_REQUEST, message = "Invalid token value"))
    public Response deleteToken(@ApiParam(hidden = true) @Auth Token authToken,
            @ApiParam(value = "Token id to delete", required = true) @PathParam("tokenId") Long tokenId) {
        User user = userDAO.findById(authToken.getUserId());
        Token token = tokenDAO.findById(tokenId);
        Helper.checkUser(user, token.getUserId());

        // invalidate cache now that we're deleting the token
        cachingAuthenticator.invalidate(token.getContent());

        tokenDAO.delete(token);

        token = tokenDAO.findById(tokenId);
        if (token == null) {
            return Response.ok().build();
        } else {
            return Response.serverError().build();
        }
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/github.com")
    @ApiOperation(value = "Add a new github.com token, used by quay.io redirect", notes = "This is used as part of the OAuth 2 web flow. "
            + "Once a user has approved permissions for Collaboratory"
            + "Their browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addGithubToken(@QueryParam("code") String code) {
        String accessToken;
        String error;
        int count = MAX_ITERATIONS;
        while (true) {
            Optional<String> asString = ResourceUtilities.asString(GIT_URL + "login/oauth/access_token?code=" + code + "&client_id="
                    + githubClientID + "&client_secret=" + githubClientSecret, null, client);

            if (asString.isPresent()) {
                Map<String, String> split = Splitter.on('&').trimResults().withKeyValueSeparator("=").split(asString.get());
                accessToken = split.get("access_token");
                error = split.get("error");
            } else {
                throw new CustomWebApplicationException("Could not retrieve github.com token based on code", HttpStatus.SC_BAD_REQUEST);
            }

            if (error != null && "bad_verification_code".equals(error)) {
                LOG.info("ERROR: {}", error);
                if (--count == 0) {
                    throw new CustomWebApplicationException("Could not retrieve github.com token based on code", HttpStatus.SC_BAD_REQUEST);
                } else {
                    LOG.info("trying again...");
                }
            } else if (accessToken != null && !accessToken.isEmpty()) {
                LOG.info("Successfully recieved accessToken: {}", accessToken);
                break;
            } else {
                LOG.info("Retrieving accessToken was unsuccessful");
                throw new CustomWebApplicationException("Could not retrieve github.com token based on code", HttpStatus.SC_BAD_REQUEST);
            }
        }

        GitHubClient githubClient = new GitHubClient();
        githubClient.setOAuth2Token(accessToken);
        long userID;
        String githubLogin;
        Token dockstoreToken = null;
        Token githubToken = null;
        try {
            UserService uService = new UserService(githubClient);
            org.eclipse.egit.github.core.User githubUser = uService.getUser();

            githubLogin = githubUser.getLogin();
        } catch (IOException ex) {
            throw new CustomWebApplicationException("Token ignored due to IOException", HttpStatus.SC_CONFLICT);
        }

        User user = userDAO.findByUsername(githubLogin);
        if (user == null) {
            user = new User();
            user.setUsername(githubLogin);
            userID = userDAO.create(user);

            // CREATE DOCKSTORE TOKEN
            final Random random = new Random();
            final int bufferLength = 1024;
            final byte[] buffer = new byte[bufferLength];
            random.nextBytes(buffer);
            String randomString = BaseEncoding.base64Url().omitPadding().encode(buffer);
            final String dockstoreAccessToken = Hashing.sha256().hashString(githubLogin + randomString, Charsets.UTF_8).toString();

            dockstoreToken = new Token();
            dockstoreToken.setTokenSource(TokenType.DOCKSTORE.toString());
            dockstoreToken.setContent(dockstoreAccessToken);
            dockstoreToken.setUserId(userID);
            dockstoreToken.setUsername(githubLogin);
            long dockstoreTokenId = tokenDAO.create(dockstoreToken);
            dockstoreToken = tokenDAO.findById(dockstoreTokenId);

        } else {
            userID = user.getId();
            List<Token> tokens = tokenDAO.findDockstoreByUserId(userID);
            if (!tokens.isEmpty()) {
                dockstoreToken = tokens.get(0);
            }

            tokens = tokenDAO.findGithubByUserId(userID);
            if (!tokens.isEmpty()) {
                githubToken = tokens.get(0);
            }
        }

        if (dockstoreToken == null) {
            LOG.info("Could not find user's dockstore token. Making new one...");
            final Random random = new Random();
            final int bufferLength = 1024;
            final byte[] buffer = new byte[bufferLength];
            random.nextBytes(buffer);
            String randomString = BaseEncoding.base64Url().omitPadding().encode(buffer);
            final String dockstoreAccessToken = Hashing.sha256().hashString(githubLogin + randomString, Charsets.UTF_8).toString();

            dockstoreToken = new Token();
            dockstoreToken.setTokenSource(TokenType.DOCKSTORE.toString());
            dockstoreToken.setContent(dockstoreAccessToken);
            dockstoreToken.setUserId(userID);
            dockstoreToken.setUsername(githubLogin);
            long dockstoreTokenId = tokenDAO.create(dockstoreToken);
            dockstoreToken = tokenDAO.findById(dockstoreTokenId);
        }

        if (githubToken == null) {
            LOG.info("Could not find user's github token. Making new one...");
            // CREATE GITHUB TOKEN
            githubToken = new Token();
            githubToken.setTokenSource(TokenType.GITHUB_COM.toString());
            githubToken.setContent(accessToken);
            githubToken.setUserId(userID);
            githubToken.setUsername(githubLogin);
            tokenDAO.create(githubToken);
            LOG.info("Github token created for {}", githubLogin);
        }

        return dockstoreToken;
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/bitbucket.org")
    @ApiOperation(value = "Add a new bitbucket.org token, used by quay.io redirect", notes = "This is used as part of the OAuth 2 web flow. "
            + "Once a user has approved permissions for Collaboratory"
            + "Their browser will load the redirect URI which should resolve here", response = Token.class)
    public Token addBitbucketToken(@ApiParam(hidden = true) @Auth Token authToken, @QueryParam("code") String code)
            throws UnsupportedEncodingException {
        if (code.isEmpty()) {
            throw new CustomWebApplicationException("Please provide an access code", HttpStatus.SC_BAD_REQUEST);
        }

        User user = userDAO.findById(authToken.getUserId());

        String url = BITBUCKET_URL + "site/oauth2/access_token";

        Optional<String> asString = ResourceUtilities.bitbucketPost(url, null, client, bitbucketClientID, bitbucketClientSecret,
                "grant_type=authorization_code&code=" + code);
        String accessToken;
        String refreshToken;
        if (asString.isPresent()) {
            LOG.info("RESOURCE CALL: {}", url);
            String json = asString.get();
            LOG.info(json);

            Gson gson = new Gson();
            Map<String, String> map = new HashMap<>();
            map = (Map<String, String>) gson.fromJson(json, map.getClass());

            accessToken = map.get("access_token");
            refreshToken = map.get("refresh_token");
        } else {
            throw new CustomWebApplicationException("Could not retrieve bitbucket.org token based on code", HttpStatus.SC_BAD_REQUEST);
        }

        String username = null;

        url = BITBUCKET_URL + "api/2.0/users/victoroicr";
        Optional<String> asString2 = ResourceUtilities.asString(url, accessToken, client);

        if (asString2.isPresent()) {
            LOG.info("RESOURCE CALL: {}", url);

            String response = asString2.get();
            Gson gson = new Gson();
            Map<String, String> map = new HashMap<>();
            map = (Map<String, String>) gson.fromJson(response, map.getClass());

            username = map.get("username");
            LOG.info("Username: {}", username);
        }

        if (user != null) {
            List<Token> tokens = tokenDAO.findBitbucketByUserId(user.getId());

            if (tokens.isEmpty()) {
                Token token = new Token();
                token.setTokenSource(TokenType.BITBUCKET_ORG.toString());
                token.setContent(accessToken);
                token.setRefreshToken(refreshToken);
                token.setUserId(user.getId());
                if (username != null) {
                    token.setUsername(username);
                } else {
                    LOG.info("Bitbucket.org token username is null, did not create token");
                    throw new CustomWebApplicationException("Username not found from resource call " + url, HttpStatus.SC_CONFLICT);
                }
                long create = tokenDAO.create(token);
                LOG.info("Bitbucket token created for {}", user.getUsername());
                return tokenDAO.findById(create);
            } else {
                LOG.info("Bitbucket token already exists for {}", user.getUsername());
                throw new CustomWebApplicationException("Bitbucket token already exists for " + user.getUsername(), HttpStatus.SC_CONFLICT);
            }
        } else {
            LOG.info("Could not find user");
            throw new CustomWebApplicationException("User not found", HttpStatus.SC_CONFLICT);
        }
    }

    @GET
    @Timed
    @UnitOfWork
    @Path("/bitbucket.org/refresh")
    @ApiOperation(value = "Refresh Bitbucket token", notes = "The Bitbucket token expire in one hour. When this happens you'll get 401 responses", response = Token.class)
    public Token refreshBitbucketToken(@ApiParam(hidden = true) @Auth Token authToken) {
        List<Token> tokens = tokenDAO.findBitbucketByUserId(authToken.getUserId());

        if (tokens.isEmpty()) {
            throw new CustomWebApplicationException("User's Bitbucket token not found.", HttpStatus.SC_BAD_REQUEST);
        }

        Token bitbucketToken = tokens.get(0);

        return Helper.refreshBitbucketToken(bitbucketToken, client, tokenDAO, bitbucketClientID, bitbucketClientSecret);
    }
}
