# openpp-android-sdk

## bintrayのバイナリを使用する場合 (※デバッグ用にOAuthはhttpで接続するようになっている)

Android Studioでbuild.gradle (Module:app) に以下を追加し、Sync Project with Gradle Filesボタンで実行。

    repositories {
        maven {
            url  "http://dl.bintray.com/webwarejp/maven"
        }
    }

    dependencies {
        compile(group: 'net.openpp.android', name: 'openpp', version: '0.2.0', ext: 'aar')
    }

## githubのソースを使用する場合 (ライブラリを修正する場合)

アプリのプロジェクトルートディレクトリでgit initする。

    $ cd MyApplication
    $ git init

ライブラリをsubmoduleとして追加

    $ git submodule add https://github.com/webwarejp/openpp-android-sdk.git

settings.gradleを以下のように設定

    include ':app', ':openpp-android-sdk:openpp'

一時的にopenpp-android-sdk/openpp配下のbuild.gradleを書き換え(エラーが出るので暫定対処)

    $ cd openpp-android-sdk/openpp
    $ vi build.gradle

apply plugin: 'com.android.library'、android、dependenciesを残して後はコメントアウトする。

    /*
    apply plugin: 'com.github.dcendents.android-maven'
    apply plugin: 'com.jfrog.bintray'
        :
        :

    task sourcesJar(type: Jar) {
        from android.sourceSets.main.java.srcDirs
        classifier = 'sources'
          :
          :
    }
    */

## AndroidManifest.xml

通知の利用の際に必要な以下のパーミッションを指定する。

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.GET_ACCOUNTS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="com.google.android.c2dm.permission.RECEIVE" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>

以下のパーミッションを指定する。android:name のnet.openpp.pushsampleapp の部分は実行するアプリケーションのパッケージ名に置き換える。

    <permission
        android:name="net.openpp.android.pushsampleapp.permission.C2D_MESSAGE"
        android:protectionLevel="signature" />

    <uses-permission android:name="net.openpp.android.pushsampleapp.permission.C2D_MESSAGE" />

OAuthのブラウザによる認証を可能にするため、以下のintent-filterを設定する。
net.openpp.android.pushsampleappの部分は実行するアプリケーションのパッケージ名に置き換える。
launchModeはsingleTaskとすること。

        <activity
            android:name=".MainActivity"
            android:label="@string/app_name"
            android:launchMode="singleTask" >
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="net.openpp.android.pushsampleapp" android:host="callback" android:path="/"/>
            </intent-filter>
        </activity>

ブロードキャストレシーバを設定する。net.openpp.pushsampleapp の部分は実行するアプリケーションのパッケージ名に置き換える。

        <receiver
            android:name="net.openpp.android.push.OpenppPushBroadcastReceiver"
            android:enabled="true"
            android:exported="true"
            android:permission="com.google.android.c2dm.permission.SEND" >
            <intent-filter>
                <action android:name="com.google.android.c2dm.intent.RECEIVE" />

                <category android:name="net.openpp.android.pushsampleapp" />
            </intent-filter>
        </receiver>

インテントサービスを設定する。

        <service
            android:name="net.openpp.android.push.OpenppPushIntentService"
            android:exported="false" />

GMSを設定する。

        <meta-data
            android:name="com.google.android.gms.version"
            android:value="@integer/google_play_services_version" />

 サンプル

     <manifest xmlns:android="http://schemas.android.com/apk/res/android"
         package="net.openpp.android.pushsampleapp">

         <uses-sdk
         android:minSdkVersion="8"
         android:targetSdkVersion="17" />

         <uses-permission android:name="android.permission.INTERNET" />
         <uses-permission android:name="android.permission.GET_ACCOUNTS" />
         <uses-permission android:name="android.permission.WAKE_LOCK" />
         <uses-permission android:name="com.google.android.c2dm.permission.RECEIVE" />
         <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
         <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>

         <permission
             android:name="net.openpp.android.pushsampleapp.permission.C2D_MESSAGE"
             android:protectionLevel="signature" />

             <uses-permission android:name="net.openpp.android.pushsampleapp.permission.C2D_MESSAGE" />

         <application android:allowBackup="true" android:label="@string/app_name"
             android:icon="@mipmap/ic_launcher" android:theme="@style/AppTheme">

             <activity
                 android:name=".MainActivity"
                 android:label="@string/app_name"
                 android:launchMode="singleTask" >
                 <intent-filter>
                     <action android:name="android.intent.action.MAIN" />
                     <category android:name="android.intent.category.LAUNCHER" />
                 </intent-filter>

                 <intent-filter>
                     <action android:name="android.intent.action.VIEW" />
                     <category android:name="android.intent.category.DEFAULT" />
                     <category android:name="android.intent.category.BROWSABLE" />
                     <data android:scheme="net.openpp.android.pushsampleapp" android:host="callback" android:path="/"/>
                 </intent-filter>
             </activity>

             <receiver
                 android:name="net.openpp.android.push.OpenppPushBroadcastReceiver"
                 android:enabled="true"
                 android:exported="true"
                 android:permission="com.google.android.c2dm.permission.SEND" >
                 <intent-filter>
                     <action android:name="com.google.android.c2dm.intent.RECEIVE" />

                     <category android:name="net.openpp.android.pushsampleapp" />
                 </intent-filter>
             </receiver>

             <service
                 android:name="net.openpp.android.push.OpenppPushIntentService"
                 android:exported="false" />

             <meta-data
                 android:name="com.google.android.gms.version"
                 android:value="@integer/google_play_services_version" />
         </application>

     </manifest>


## 使い方 (Activityサンプル)
    package net.openpp.android.pushsampleapp;

    import android.app.Activity;
    import android.content.Intent;
    import android.os.Bundle;

    import net.openpp.android.push.OpenppPushManager;

    public class MainActivity extends Activity {

        OpenppPushManager manager = OpenppPushManager.getInstance();

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            manager.setSenderId("xxxxxxxxxxxx"); // google developerコンソールで取得したアプリのプロジェクト番号
            manager.setApiKey("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"); // OAuth API Key
            manager.setApiSecret("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"); // OAuth API Secret
            manager.setRegistrationServerName("xxxxxx.xxxxxxxxx.co.jp"); // push通知デバイス登録用サーバ名
            manager.setAuthServerName("xxxxxx.xxxxxxxxx.co.jp"); // OAuth認証サーバ名
            manager.setResourceServerName("xxxxxx.xxxxxxxxx.co.jp"); // 会員情報APIを有しているサーバ名
            manager.setWakeupActivity(MainActivity.class); // push通知クリック時に起動するアクティビティ
            manager.register(this);
        }

        @Override
        public void onNewIntent(Intent intent) {
            super.onNewIntent(intent);
            manager.parseIntent(intent);
        }
    }