To run the code from command line, first compile with:

>> javac ImageReader.java

and then, you can run to read a sample image (image1.rgb) as:

>> java ImageReader image1.rgb 5 1 100

where, the first parameter is the image file name, second is quantization level, third is delivery mode and last is latency. Image width should be 352 and height should be 288.