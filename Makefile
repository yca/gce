keygen:
	@keytool -genkeypair -validity 10000 -dname "CN=Bilkon,OU=arge,O=Bilkon,L=Ankara,S=Ankara,C=tr" -keystore ~/AndroidTest.keystore -storepass mynewpass -keypass mynewpass -alias AndroidTestKey -keyalg RSA -v

compile:
	@ant release

sign: compile
	@jarsigner -verbose -keystore ~/AndroidTest.keystore -storepass mynewpass -keypass mynewpass -signedjar bin/ecgview-signed.apk bin/EcgViewActivity-release-unsigned.apk AndroidTestKey

install: sign
	@/home/caglar/myfs/software/android-sdk-linux/platform-tools/adb install -r bin/ecgview-signed.apk

build: install
