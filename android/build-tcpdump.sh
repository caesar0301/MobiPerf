rm -rf obj/
rm res/raw/tcpdump
ndk-build
cp obj/local/armeabi/tcpdump res/raw/tcpdump
exit 0;
