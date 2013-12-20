 #!/sbin/sh
 KEY=$1
 if [[ -z "$KEY" ]]; then
   echo "Pass path to the keystore"
   exit -1
 fi
 echo "Release tool, using key"
 echo "Getting ready for build"
 android update project --path .
 echo "Removing old release"
 rm -rf bin/ 
 ant release
 echo "Signing build"
 jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore $KEY bin/MainActivity-release-unsigned.apk humpolec
 mv bin/MainActivity-release-unsigned.apk bin/UbuntuInstaller.apk
 echo "Signed relase is at bin/UbuntuInstaller.apk"