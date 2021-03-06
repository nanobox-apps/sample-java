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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.dropwizard.views.View;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

/**
 *
 * @author dyuen
 */
@Path("/integration.github.com")
@Api("/integration.github.com")
@Produces(MediaType.TEXT_HTML)
public class GitHubComAuthenticationResource {
    private final String clientID;
    private final String redirectURI;

    public GitHubComAuthenticationResource(String clientID, String redirectURI) {
        this.clientID = clientID;
        this.redirectURI = redirectURI;
    }

    @GET
    @ApiOperation(value = "Display an authorization link for github.com", notes = "This is a stop-gap GUI for displaying a link that allows a user to start the OAuth 2 web flow", response = GithubComView.class)
    public GithubComView getView() {
        return new GithubComView();
    }

    /**
     * @return the clientID
     */
    public String getClientID() {
        return clientID;
    }

    /**
     * @return the redirectURI
     */
    public String getRedirectURI() {
        return redirectURI;
    }

    public class GithubComView extends View {
        private final GitHubComAuthenticationResource parent;

        public GithubComView() {
            super("github.com.auth.view.ftl");
            parent = GitHubComAuthenticationResource.this;
        }

        /**
         * @return the parent
         */
        public GitHubComAuthenticationResource getParent() {
            return parent;
        }
    }

}
