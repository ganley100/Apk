p='game/AndroidManifest.xml'
s=open(p,encoding='utf8').read()
act='''<activity android:name="tvfix.ObbImportActivity" android:label="Import OBB" android:exported="true" android:theme="@android:style/Theme.DeviceDefault">
<intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/><category android:name="android.intent.category.LEANBACK_LAUNCHER"/></intent-filter>
</activity>
'''
import re
# make the importer the only Android TV launcher entry for this package
s=re.sub(r'<category\s+android:name="android.intent.category.LEANBACK_LAUNCHER"\s*/>','',s)
assert '</application>' in s
s=s.replace('</application>',act+'</application>',1)
if 'android.permission.INTERNET' not in s:
    s=s.replace('<application','<uses-permission android:name="android.permission.INTERNET"/><application',1)
for perm in ('READ_EXTERNAL_STORAGE','WRITE_EXTERNAL_STORAGE'):
    if 'android.permission.'+perm not in s:
        s=s.replace('<application','<uses-permission android:name="android.permission.'+perm+'"/><application',1)
open(p,'w',encoding='utf8').write(s)
