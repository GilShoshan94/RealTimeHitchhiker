/**
 * Created by gilshoshan on 09/11/17.
 */

Profile fbProfile=Profile.getCurrentProfile();
        AccessTokenTracker accessTokenTracker=new AccessTokenTracker(){
@Override
protected void onCurrentAccessTokenChanged(AccessToken oldAccessToken,
        AccessToken currentAccessToken){
        if(currentAccessToken==null){
        Log.d(TAG,"onLogout catched");
        deleteContact();//This is my code
        }
        }
        };
        if(fbProfile==null){
        Log.d(TAG,"NOT logged in");
        callbackManager=CallbackManager.Factory.create();

        LoginButton loginButton=(LoginButton)findViewById(R.id.login_button);
        loginButton.setReadPermissions("email");

        // Callback registration
        loginButton.registerCallback(callbackManager,new FacebookCallback<LoginResult>(){
@Override
public void onSuccess(LoginResult loginResult){
        Log.d(TAG,"onSuccess login Facebook");
        GraphRequest request=GraphRequest.newMeRequest(
        AccessToken.getCurrentAccessToken(),
        new GraphRequest.GraphJSONObjectCallback(){
@Override
public void onCompleted(JSONObject object,GraphResponse response){
        FacebookSdk.setIsDebugEnabled(true);
        FacebookSdk.addLoggingBehavior(LoggingBehavior.INCLUDE_ACCESS_TOKENS);

        Log.d(TAG,"AccessToken.getCurrentAccessToken() "+AccessToken.getCurrentAccessToken().toString());
        Profile profile=Profile.getCurrentProfile();
        Log.d(TAG,"Current profile: "+profile);
        if(profile!=null){
        Log.d(TAG,String.format("id = %s; name = %s; lastName = %s; uri = %s",
        profile.getId(),profile.getFirstName(),
        profile.getLastName(),profile.getProfilePictureUri(50,60)));
        name=String.format("%s %s",profile.getFirstName(),profile.getLastName());
        fbid=profile.getId();
        pushNewContact();//This is my code
        }
        }
        });
        request.executeAsync();
        }

@Override
public void onCancel(){
        Log.d(TAG,"onCancel");
        }

@Override
public void onError(FacebookException e){
        Log.e(TAG,"onError",e);
        }
        });
        }else{
        Log.d(TAG,"Logged with "+fbProfile.getName());
        fbid=fbProfile.getId();
        }
        accessTokenTracker.startTracking();