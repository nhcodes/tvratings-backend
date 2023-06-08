package codes.nh.tvratings.server;

import codes.nh.tvratings.Application;
import codes.nh.tvratings.database.ImdbDatabase;
import codes.nh.tvratings.database.UserDatabase;
import codes.nh.tvratings.utils.JWTManager;
import codes.nh.tvratings.utils.RecaptchaManager;
import codes.nh.tvratings.utils.Utils;
import codes.nh.tvratings.utils.VerificationCodeManager;
import io.javalin.Javalin;
import io.javalin.community.ssl.SSLConfig;
import io.javalin.community.ssl.SSLPlugin;
import io.javalin.config.JavalinConfig;
import io.javalin.http.*;
import io.javalin.http.util.NaiveRateLimit;
import io.javalin.plugin.bundled.CorsContainer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * This class contains the API server and endpoints.
 */
public class APIServer {

    private final int port;

    private ImdbDatabase imdbDatabase;

    private UserDatabase userDatabase;

    private Javalin server;

    public APIServer(int port, ImdbDatabase imdbDatabase, UserDatabase userDatabase) {
        this.port = port;
        this.imdbDatabase = imdbDatabase;
        this.userDatabase = userDatabase;
    }

    public ImdbDatabase getImdbDatabase() {
        return imdbDatabase;
    }

    public void setImdbDatabase(ImdbDatabase imdbDatabase) {
        this.imdbDatabase = imdbDatabase;
    }

    /**
     * Starts the API server. Stops any running server.
     */
    public void start() {

        if (server != null) {
            stop();
            return;
        }

        server = Javalin.create(getJavalinConfig())

                .get("", context -> context.result("hello world"))
                .get("/search", getSearchHandler())
                .get("/show", getShowHandler())
                .get("/genres", getGenresHandler())
                .post("/login", postLoginHandler())
                .get("/followlist", getFollowListHandler())
                .get("/follow", getFollowHandler())

                .exception(Exception.class, (exception, context) -> { //todo
                    Utils.log("server error: " + exception.getMessage());
                    respondFailure(context, HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
                })

                .start();
    }

    /**
     * Stops the API server.
     */
    public void stop() {
        server.close();
    }

    private void respondSuccess(Context context, String responseJson) {
        context.contentType(ContentType.APPLICATION_JSON).result(responseJson);
    }

    private void respondFailure(Context context, HttpStatus status, String errorMessage) {
        JSONObject errorJson = new JSONObject();
        errorJson.put("error", errorMessage);
        context.status(status).contentType(ContentType.APPLICATION_JSON).result(errorJson.toString());
    }

    //==========[Configuration]==========

    private Consumer<JavalinConfig> getJavalinConfig() {
        return config -> {

            if (Application.configuration.sslEnabled) {
                config.plugins.enableSslRedirects();
            }

            config.plugins.register(new SSLPlugin(getSSLConfig()));

            config.plugins.enableCors(getCorsConfig());

        };
    }

    private Consumer<SSLConfig> getSSLConfig() {
        return sslConfig -> {
            if (Application.configuration.sslEnabled) {
                sslConfig.insecure = false;
                sslConfig.secure = true;
                sslConfig.sniHostCheck = false; //BadMessageException: 400: Invalid SNI
                sslConfig.pemFromPath(Application.configuration.sslCertificatePath, Application.configuration.sslPrivateKeyPath);
            } else {
                sslConfig.insecure = true;
                sslConfig.secure = false;
            }
            sslConfig.insecurePort = port;
            sslConfig.securePort = port;
        };
    }

    private Consumer<CorsContainer> getCorsConfig() {
        return cors -> cors.add(corsConfig -> {
            corsConfig.allowHost(Application.configuration.corsHost);
            corsConfig.allowCredentials = true;
            //corsConfig.exposeHeader("Set-Cookie");
        });
    }

    //==========[Endpoints]==========

    /**
     * example: /search?type=shows&sortColumn=VoTeS&minRating=9&sortOrder=desc&genres=DRAMA,crime&pageLimit=10&pageNumber=0
     *
     * @return The /search endpoint handler.
     */
    private Handler getSearchHandler() {
        return context -> {

            Utils.log(context.ip() + " getSearchHandler");

            JSONArray resultJson = imdbDatabase.search(
                    context.queryParam("type"),
                    context.queryParam("titleSearch"),
                    context.queryParam("minVotes"),
                    context.queryParam("maxVotes"),
                    context.queryParam("minRating"),
                    context.queryParam("maxRating"),
                    context.queryParam("minYear"),
                    context.queryParam("maxYear"),
                    context.queryParam("minDuration"),
                    context.queryParam("maxDuration"),
                    context.queryParam("genres"),
                    context.queryParam("sortColumn"),
                    context.queryParam("sortOrder"),
                    context.queryParam("pageNumber"),
                    context.queryParam("pageLimit")
            );

            respondSuccess(context, resultJson.toString());

        };
    }

    /**
     * example: /show?showId=tt0903747
     *
     * @return The /show endpoint handler.
     */
    private Handler getShowHandler() {
        return context -> {

            Utils.log(context.ip() + " getShowHandler");

            String showId = context.queryParam("showId");
            if (showId == null) {
                respondFailure(context, HttpStatus.BAD_REQUEST, "showId not found");
                return;
            }

            JSONArray showJson = imdbDatabase.getShow(showId);
            if (showJson.isEmpty()) {
                respondFailure(context, HttpStatus.NOT_FOUND, "showId " + showId + " not found");
                return;
            }

            Utils.log(showJson.toString());

            JSONArray episodesJson = imdbDatabase.getShowEpisodes(showId);

            JSONObject resultJson = new JSONObject();
            resultJson.put("show", showJson.getJSONObject(0));
            resultJson.put("episodes", episodesJson);

            respondSuccess(context, resultJson.toString());

        };
    }

    /**
     * example: /genres
     *
     * @return The /genres endpoint handler.
     */
    private Handler getGenresHandler() {
        return context -> {

            JSONArray resultJson = imdbDatabase.getGenres();
            respondSuccess(context, resultJson.toString());

        };
    }

    /**
     * example: /login<br>
     * body: json of (email, recaptcha) to request a verification code
     * or (email, recaptcha, verification code) to log in.
     *
     * @return The /login endpoint handler.
     */
    private Handler postLoginHandler() {
        return context -> {

            try {
                NaiveRateLimit.requestPerTimeUnit(context, 3, TimeUnit.MINUTES);
            } catch (HttpResponseException e) {
                respondFailure(context, HttpStatus.TOO_MANY_REQUESTS, "too many requests, try again in a minute");
                return;
            }

            JSONObject loginJson = new JSONObject(context.body());

            String email = loginJson.optString("email", null);
            if (email == null) {
                respondFailure(context, HttpStatus.BAD_REQUEST, "email not found");
                return;
            }

            String recaptcha = loginJson.optString("recaptcha", null);
            if (!recaptchaManager.verifyToken(recaptcha)) {
                respondFailure(context, HttpStatus.BAD_REQUEST, "recaptcha not found or invalid");
                return;
            }

            String code = loginJson.optString("code", null);
            if (code == null) {
                //generate verification code, put it in db, and send it to email

                String verificationCode = verificationCodeManager.generateVerificationCode();
                userDatabase.addVerificationCode(email, verificationCode);
                Utils.log(email + " send verification code " + verificationCode);
                try {
                    sendVerificationMail(email, verificationCode);
                    respondSuccess(context, "{}");
                } catch (Exception e) {
                    Utils.log("error while sending verification mail (" + email + "): " + e.getMessage());
                    respondFailure(context, HttpStatus.INTERNAL_SERVER_ERROR, "sending mail failed");
                }

            } else {
                //check if email-code exists in db, if so generate jwt and sent it using Set-Cookie header

                boolean valid = userDatabase.checkVerificationCode(email, code);
                Utils.log(email + " check verification code " + valid);
                if (valid) {
                    setJWTCookie(context, email);
                    respondSuccess(context, "{}");
                } else {
                    respondFailure(context, HttpStatus.BAD_REQUEST, "verification code invalid");
                }

            }

        };
    }

    /**
     * example: /followlist
     *
     * @return The /followlist endpoint handler.
     */
    private Handler getFollowListHandler() {
        return context -> {

            String email = getJWTEmailFromCookie(context);
            if (email == null) {
                respondFailure(context, HttpStatus.UNAUTHORIZED, "user not authenticated");
                return;
            }

            Utils.log(email + " getFollowListHandler");

            JSONArray followsJson = userDatabase.getFollowedShows(email, imdbDatabase.getDatabasePath());
            respondSuccess(context, followsJson.toString());

        };
    }

    /**
     * example: /follow?showId=tt0903747&follow=true
     *
     * @return The /follow endpoint handler.
     */
    private Handler getFollowHandler() {
        return context -> {

            String showId = context.queryParam("showId");
            Boolean follow = Utils.stringToBooleanOrNull(context.queryParam("follow"));
            if (showId == null || follow == null) {
                respondFailure(context, HttpStatus.BAD_REQUEST, "showId or follow not found");
                return;
            }

            String email = getJWTEmailFromCookie(context);
            if (email == null) {
                respondFailure(context, HttpStatus.UNAUTHORIZED, "user not authenticated");
                return;
            }

            if (follow) {
                userDatabase.followShow(email, showId);
                Utils.log(email + " followed " + showId);
            } else {
                userDatabase.unfollowShow(email, showId);
                Utils.log(email + " unfollowed " + showId);
            }

            JSONArray followsJson = userDatabase.getFollowedShows(email, imdbDatabase.getDatabasePath());
            respondSuccess(context, followsJson.toString());

        };
    }

    //==========[Verification Code]==========

    private final VerificationCodeManager verificationCodeManager = new VerificationCodeManager();

    private void sendVerificationMail(String email, String verificationCode) throws Exception {
        String subject = "your verification code";
        String content = "<html><h3>your verification code: %s</h3></html>".formatted(verificationCode);
        Application.mailManager.sendMail(email, subject, content);
    }

    //==========[Google Recaptcha]==========

    private final RecaptchaManager recaptchaManager = new RecaptchaManager();

    //==========[JWT Authentication]==========

    /*
    https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie
    https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies
    */

    private final JWTManager jwtManager = new JWTManager(Application.configuration.jwtSecretKey);

    private void setJWTCookie(Context context, String email) {
        String token = jwtManager.createJWT(email);
        Cookie cookie = new Cookie("jwt", token);
        cookie.setMaxAge(Application.configuration.jwtExpireSeconds);
        cookie.setSameSite(SameSite.STRICT);
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        context.cookie(cookie);
    }

    private String getJWTEmailFromCookie(Context context) {
        String token = context.cookie("jwt");
        if (token == null) return null;
        return jwtManager.verifyJWT(token);
    }

}
