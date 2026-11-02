p='game/AndroidManifest.xml'
s=open(p,encoding='utf8').read()
act='''<activity android:name="tvfix.ObbImportActivity" android:label="Import OBB" android:exported="true" android:theme="@android:style/Theme.DeviceDefault">
<intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/><category android:name="android.intent.category.LEANBACK_LAUNCHER"/></intent-filter>
</activity>
'''
assert '</application>' in s
s=s.replace('</application>',act+'</application>',1)
if 'android.permission.INTERNET' not in s:
    s=s.replace('<application','<uses-permission android:name="android.permission.INTERNET"/><application',1)
open(p,'w',encoding='utf8').write(s)
