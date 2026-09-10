// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
// GENERATED — do not edit. Run tools/codegen/generate.sh.
package app.gauja.core.api.apis

import app.gauja.core.api.infrastructure.CollectionFormats.*
import retrofit2.http.*
import retrofit2.Response
import okhttp3.RequestBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import app.gauja.core.api.models.GetAuthJellyfinQuickconnectCheck200Response
import app.gauja.core.api.models.PostAuthJellyfinQuickconnectAuthenticateRequest
import app.gauja.core.api.models.PostAuthJellyfinQuickconnectInitiate200Response
import app.gauja.core.api.models.PostAuthJellyfinRequest
import app.gauja.core.api.models.PostAuthLocal200Response
import app.gauja.core.api.models.PostAuthLocalRequest
import app.gauja.core.api.models.PostAuthLogout200Response
import app.gauja.core.api.models.PostAuthPlexRequest
import app.gauja.core.api.models.User

interface AuthApi {
    /**
     * GET auth/jellyfin/quickconnect/check
     * Check Quick Connect authorization status
     * Checks if the Quick Connect code has been authorized by the user.
     * Responses:
     *  - 200: Authorization status returned
     *  - 404: Quick Connect session not found or expired
     *
     * @param secret The secret returned from the initiate endpoint
     * @return [GetAuthJellyfinQuickconnectCheck200Response]
     */
    @GET("auth/jellyfin/quickconnect/check")
    suspend fun getAuthJellyfinQuickconnectCheck(@Query("secret") secret: kotlin.String): Response<GetAuthJellyfinQuickconnectCheck200Response>

    /**
     * GET auth/me
     * Get logged-in user
     * Returns the currently logged-in user.
     * Responses:
     *  - 200: Object containing the logged-in user in JSON
     *
     * @return [User]
     */
    @GET("auth/me")
    suspend fun getAuthMe(): Response<User>

    /**
     * POST auth/jellyfin
     * Sign in using a Jellyfin username and password
     * Takes the user&#39;s username and password to log the user in. Generates a session cookie for use in further requests. If the user does not exist, and there are no other users, then a user will be created with full admin privileges. If a user logs in with access to the Jellyfin server, they will also have an account created, but without any permissions.
     * Responses:
     *  - 200: OK
     *
     * @param postAuthJellyfinRequest 
     * @return [User]
     */
    @POST("auth/jellyfin")
    suspend fun postAuthJellyfin(@Body postAuthJellyfinRequest: PostAuthJellyfinRequest): Response<User>

    /**
     * POST auth/jellyfin/quickconnect/authenticate
     * Authenticate with Quick Connect
     * Completes the Quick Connect authentication flow and creates a user session.
     * Responses:
     *  - 200: Successfully authenticated
     *  - 403: Quick Connect not authorized or access denied
     *  - 500: Authentication failed
     *
     * @param postAuthJellyfinQuickconnectAuthenticateRequest 
     * @return [User]
     */
    @POST("auth/jellyfin/quickconnect/authenticate")
    suspend fun postAuthJellyfinQuickconnectAuthenticate(@Body postAuthJellyfinQuickconnectAuthenticateRequest: PostAuthJellyfinQuickconnectAuthenticateRequest): Response<User>

    /**
     * POST auth/jellyfin/quickconnect/initiate
     * Initiate Jellyfin Quick Connect
     * Initiates a Quick Connect session and returns a code for the user to authorize on their Jellyfin server.
     * Responses:
     *  - 200: Quick Connect session initiated
     *  - 500: Failed to initiate Quick Connect
     *
     * @return [PostAuthJellyfinQuickconnectInitiate200Response]
     */
    @POST("auth/jellyfin/quickconnect/initiate")
    suspend fun postAuthJellyfinQuickconnectInitiate(): Response<PostAuthJellyfinQuickconnectInitiate200Response>

    /**
     * POST auth/local
     * Sign in using a local account
     * Takes an &#x60;email&#x60; and a &#x60;password&#x60; to log the user in. Generates a session cookie for use in further requests.
     * Responses:
     *  - 200: OK
     *
     * @param postAuthLocalRequest 
     * @return [PostAuthLocal200Response]
     */
    @POST("auth/local")
    suspend fun postAuthLocal(@Body postAuthLocalRequest: PostAuthLocalRequest): Response<PostAuthLocal200Response>

    /**
     * POST auth/logout
     * Sign out and clear session cookie
     * Completely clear the session cookie and associated values, effectively signing the user out.
     * Responses:
     *  - 200: OK
     *
     * @return [PostAuthLogout200Response]
     */
    @POST("auth/logout")
    suspend fun postAuthLogout(): Response<PostAuthLogout200Response>

    /**
     * POST auth/plex
     * Sign in using a Plex token
     * Takes an &#x60;authToken&#x60; (Plex token) to log the user in. Generates a session cookie for use in further requests. If the user does not exist, and there are no other users, then a user will be created with full admin privileges. If a user logs in with access to the main Plex server, they will also have an account created, but without any permissions.
     * Responses:
     *  - 200: OK
     *
     * @param postAuthPlexRequest 
     * @return [User]
     */
    @POST("auth/plex")
    suspend fun postAuthPlex(@Body postAuthPlexRequest: PostAuthPlexRequest): Response<User>

}
