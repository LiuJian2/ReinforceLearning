echo "Start Encrypt"

function buildSource() {
    echo "Build SourceApp"
    cd SourceApp
    ../gradlew clean assembleDebug
    cp build/outputs/apk/SourceApp-debug.apk ../Encryptproject/Source.apk
    cd ..
}

function buildReinforce() {
    echo "Build ReinforceApp"
    cd ReinforceApp
    ../gradlew clean assembleDebug
    cp build/outputs/apk/ReinforceApp-debug.apk ../Encryptproject/Reinforce.apk
    cd ..
}

function unzipReinforce() {
   echo "unzip ReinforceApp dex"
   cd Encryptproject
   mkdir tmp
   cp Reinforce.apk tmp/
   cd tmp
   unzip -o Reinforce.apk
   cp classes.dex ../
   cd ..
   rm -rf tmp
   cd ..
}

function mergeDexAndApk() {
    cd Encryptproject
    projectPath=`pwd`
    echo $projectPath

    cd src/main/java

    javac com/encrypt/Encrypt.java
    java com/encrypt/Encrypt ${projectPath}/Source.apk ${projectPath}/classes.dex ${projectPath}/encrypted-classes.dex

    rm ${projectPath}/Source.apk
    rm ${projectPath}/classes.dex
    mv ${projectPath}/encrypted-classes.dex ${projectPath}/classes.dex

    cd ${projectPath}
    zip -m Reinforce.apk classes.dex
}

buildSource  //构建SourceApp
buildReinforce //构建ReinforceApp
unzipReinforce // 获取Reinforce dex
mergeDexAndApk // 合并Dex同时把Dex放到Reinforce.apk中

// 对Apk签名, 生成Reinforce-Signed.apk
jarsigner -verbose -keystore ../learning.jks -storepass 123456 -keypass 123456 -sigfile CERT -digestalg SHA1 -sigalg MD5withRSA -signedjar Reinforce-Signed.apk Reinforce.apk learning
rm -f Reinforce.apk
echo "加固成功!"