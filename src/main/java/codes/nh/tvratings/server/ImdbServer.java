package codes.nh.tvratings.server;

import codes.nh.tvratings.Application;
import codes.nh.tvratings.database.ImdbDatabase;
import codes.nh.tvratings.database.UserDatabase;
import codes.nh.tvratings.utils.JWTManager;
import codes.nh.tvratings.utils.Utils;
import io.javalin.Javalin;
import io.javalin.community.ssl.SSLPlugin;
import io.javalin.http.*;
import io.javalin.http.util.NaiveRateLimit;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

/**
 * this class contains the imdb api server and the underlying endpoints
 */
public class ImdbServer {

    private final int port;

    private ImdbDatabase imdbDatabase;

    private UserDatabase userDatabase;

    private Javalin server;

    public ImdbServer(int port, ImdbDatabase imdbDatabase, UserDatabase userDatabase) {
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
     * starts the server. stops any running server
     */
    public void start() {

        if (server != null) {
            stop();
            return;
        }

        server = Javalin.create(config -> {
                    config.plugins.register(new SSLPlugin(sslConfig -> {
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
                    }));
                    if (Application.configuration.sslEnabled) {
                        config.plugins.enableSslRedirects();
                    }
                    config.plugins.enableCors(corsContainer -> {
                        corsContainer.add(corsConfig -> {
                            corsConfig.allowHost(Application.configuration.corsHost);
                            corsConfig.allowCredentials = true;
                            //it.exposeHeader("Set-Cookie");
                        });
                    });
                })

                .get("", context -> {
                    context.result("hello world");
                })
                .get("/search", getSearchHandler())
                .get("/show", getShowHandler())
                .get("/genres", getGenresHandler())
                .post("/login", postLoginHandler())
                .get("/followlist", getFollowsHandler())
                .get("/follow", getFollowHandler())

                .exception(Exception.class, (exception, context) -> { //todo
                    Utils.log(exception.getMessage());
                    respondFailure(context, HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage());
                })

                .start();
    }

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

    /**
     * /search
     * example: http://localhost:7070/search?type=shows&sortColumn=VoTeS&minRating=9&sortOrder=desc&genres=DRAMA,crime&pageLimit=10&pageNumber=0
     *
     * @return the search endpoint handler
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
     * /show
     * example: http://localhost:7070/show?showId=tt0903747
     *
     * @return the show endpoint handler
     */
    private Handler getShowHandler() {
        return context -> {

            Utils.log(context.ip() + " getShowHandler ");

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
     * /genres
     * example: http://localhost:7070/genres
     *
     * @return the genres endpoint handler
     */
    private Handler getGenresHandler() {
        return context -> {

            JSONArray resultJson = imdbDatabase.getGenres();
            respondSuccess(context, resultJson.toString());

        };
    }

    /**
     * /login
     * example: http://localhost:7070/login
     * body: json of (email, recaptcha) to request verification code or (email, recaptcha, verification code) to log in
     *
     * @return the login endpoint handler
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
            if (recaptcha == null || !Utils.verifyRecaptchaToken(recaptcha)) {
                respondFailure(context, HttpStatus.BAD_REQUEST, "recaptcha not found or invalid");
                return;
            }

            String code = loginJson.optString("code", null);
            if (code == null) {
                //generate verification code, put it in db, and send it to email

                String verificationCode = Utils.generateVerificationCode();
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
     * /followlist
     * example: http://localhost:7070/followlist
     *
     * @return the followlist endpoint handler
     */
    private Handler getFollowsHandler() {
        return context -> {

            String email = getJWTEmailFromCookie(context);
            if (email == null) {
                respondFailure(context, HttpStatus.UNAUTHORIZED, "user not authenticated");
                return;
            }

            Utils.log(email + " getFollows");

            JSONArray followsJson = userDatabase.getFollowedShows(email, imdbDatabase.getDatabasePath());
            respondSuccess(context, followsJson.toString());

        };
    }

    /**
     * /follow
     * example: http://localhost:7070/follow?showId=tt0903747&follow=true
     *
     * @return the follow endpoint handler
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

    private void sendVerificationMail(String email, String verificationCode) throws Exception {
        String subject = "your verification code";
        String content = "<html><h5>your verification code: </h5><h3>" + verificationCode + "</h3></html>";
        Application.mailManager.sendMail(email, subject, content);
    }

    //==========[Authentication]==========

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
