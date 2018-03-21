
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.concurrent.TimeUnit;

import javax.swing.*;

public class ImageReader {
	class Color {
		float r;
		float g;
		float b;

		public Color() {
			this.r = 0;
			this.g = 0;
			this.b = 0;
		}

		public Color(float r, float g, float b) {
			this.r = r;
			this.g = g;
			this.b = b;
		}

		public float getChannel(int i) {
			if (i == 0)
				return r;
			if (i == 1)
				return g;
			return b;
		}

		public void setChannel(int i, float value) {
			if (i == 0)
				r = value;
			else if (i == 1)
				g = value;
			else
				b = value;
		}
	}
	JPanel p;
	JFrame frame;
	JLabel lbIm1;
	JLabel lbIm2;
	BufferedImage img;
	BufferedImage img1;
	Color[][] DCT;
	Color[][] decoder;
	GridBagConstraints con;

	private void print(Color[][] color, int x, int y) {
		for (int i = x; i < x + 8; i++) {
			for (int j = y; j < y + 8; j++) {
				System.out.print(color[i][j].getChannel(0) + " ");
			}
			System.out.println();
		}
		System.out.println();
	}

	private void show() {
		JLabel temp = new JLabel(new ImageIcon(img1));
		p.remove(lbIm2);
		lbIm2 = temp;
		p.add(lbIm2, con);
		p.revalidate();
		p.repaint();
	}
	private void display(int row, int col) {
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				int pix = 0xff000000 | ((int) decoder[row + x][col + y].getChannel(0) << 16)
						| ((int) decoder[row + x][col + y].getChannel(1) << 8)
						| (int) decoder[row + x][col + y].getChannel(2);
				img1.setRGB(col + y, row + x, pix);
			}
		}
	}

	// sequential mode
	private void sequential(int width, int height, int n, long milliseconds) {
		for (int i = 0; i < height / 8; i++) {
			for (int j = 0; j < width / 8; j++) {
				for (int c = 0; c <= 2; c++) {
					dequantization(i * 8, j * 8, n, c);
					inverseDCT(DCT, i * 8, j * 8, c);
				}
				display(i * 8, j * 8);
				show();
				delay(milliseconds);
			}
		}
	}

	// progressive
	private void progressive(int width, int height, int n, long milliseconds) {
		for (int i = 0; i < height / 8; i++) {
			for (int j = 0; j < width / 8; j++) {
				for (int c = 0; c <= 2; c++) {
					dequantization(i * 8, j * 8, n, c);
				}
			}
		}
		// zigzag
		Color[][] color = new Color[height][width];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				color[y][x] = new Color();
			}
		}
		for (int u = 0; u < 15; u++) {
			for (int v = 0; v < Math.min(u + 1, 15 - u); v++) {
				int dctRow = 0, dctCol = 0;
				int r = Math.min(u, 7);
				if (u % 2 == 0) {
					dctRow = r - v;
					dctCol = u - r + v;
				} else {
					dctRow = u - r + v;
					dctCol = r - v;
				}
				// for (int dctRow = 0; dctRow < 8; dctRow++) {
				// for (int dctCol = 0; dctCol < 8; dctCol++) {
				for (int i = 0; i < height / 8; i++) {
					for (int j = 0; j < width / 8; j++) {
						color[i * 8 + dctRow][j * 8 + dctCol] = DCT[i * 8 + dctRow][j * 8 + dctCol];
						for (int c = 0; c <= 2; c++) {
							inverseDCT(color, i * 8, j * 8, c);
						}
						display(i * 8, j * 8);
					}
				}
				show();
				delay(milliseconds);
				// }
				// }

			}
		}
	}

	private void progressiveBit(int width, int height, int n, long milliseconds) {
		Color[][] color = new Color[height][width];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				color[y][x] = new Color();
			}
		}
		
		for (int len = 0; len < 13; len++) {
			for (int i = 0; i < height / 8; i++) {
				for (int j = 0; j < width / 8; j++) {
					for (int c = 0; c <= 2; c++) {
						dequantization(color, i * 8, j * 8, n, c, len);
						inverseDCT(color, i * 8, j * 8, c);
					}
					display(i * 8, j * 8);
				}
			}
			show();
			delay(milliseconds);
		}
	}

	// encoder
	private void encoder(int width, int height, int n) {
		for (int i = 0; i < height / 8; i++) {
			for (int j = 0; j < width / 8; j++) {
				for (int c = 0; c <= 2; c++) {
					toDCT(i * 8, j * 8, c);
					quantization(i * 8, j * 8, n, c);
				}
			}
		}
	}

	private void toDCT(int row, int col, int channel) {
		// u,v = 0, C(u)C(v) = 1 / sqrt(2)
		float[][] tmp = new float[8][8];
		for (int u = 0; u < 8; u++) {
			for (int v = 0; v < 8; v++) {
				float sum = 0;
				for (int x = 0; x < 8; x++) {
					for (int y = 0; y < 8; y++) {
						if (u == 0 && v == 0) {
							tmp[x][y] = DCT[row + x][col + y].getChannel(channel);
						}
						sum += tmp[x][y] * Math.cos(((2 * x + 1) / 16.0 * u * Math.PI))
								* Math.cos(((2 * y + 1) / 16.0 * v * Math.PI));
					}
				}
				float cu = 1, cv = 1;
				if (u == 0) {
					cu = (float) (1 / Math.sqrt(2));
				}
				if (v == 0) {
					cv = (float) (1 / Math.sqrt(2));
				}
				DCT[row + u][col + v].setChannel(channel, sum / 4 * cu * cv);
			}
		}
	}

	private void quantization(int row, int col, int n, int c) {
		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 8; j++) {
				DCT[row + i][col + j].setChannel(c, Math.round(DCT[row + i][col + j].getChannel(c) / Math.pow(2, n)));
			}
		}
	}

	private void dequantization(int row, int col, int n, int c) {
		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 8; j++) {
				DCT[row + i][col + j].setChannel(c, (float) (DCT[row + i][col + j].getChannel(c) * Math.pow(2, n)));
			}
		}
	}
	
	private void dequantization(Color[][] color, int row, int col, int n, int c, int l) {
		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 8; j++) {
				int value = (int)DCT[row + i][col + j].getChannel(c);
				boolean flag = false;
				if (value < 0) {
					value = -value;
					flag = true;
				}
				String binary = Integer.toBinaryString(value);
				int len = binary.length();
				int num = 0;
				for (int k = 0; k < Math.min(len, l + 1);k++) {
					num += (int)Math.pow(2, len - 1 - k);
				}
				value = value & num;
				if (flag) value = -value;

				color[row + i][col + j].setChannel(c, 
						(float) (value * Math.pow(2, n)));
			}
		}
	}
	private void inverseDCT(Color[][] c, int row, int col, int channel) {
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				float sum = 0;
				for (int u = 0; u < 8; u++) {
					for (int v = 0; v < 8; v++) {
						float cu = 1, cv = 1;
						if (u == 0) {
							cu = (float) (1 / Math.sqrt(2));
						}
						if (v == 0) {
							cv = (float) (1 / Math.sqrt(2));
						}
						sum += cu * cv * c[row + u][col + v].getChannel(channel)
								* Math.cos(((2 * x + 1) * u * Math.PI) / 16)
								* Math.cos(((2 * y + 1) * v * Math.PI) / 16);
					}
				}
				if (sum / 4 > 255) {
					decoder[row + x][col + y].setChannel(channel, 255);
				} else if (sum / 4 < 0) {
					decoder[row + x][col + y].setChannel(channel, 0);
				} else {
					decoder[row + x][col + y].setChannel(channel, Math.round(sum / 4));
				}
			}
		}
	}

	private void delay(long milliseconds) {
		try {
			TimeUnit.SECONDS.sleep(milliseconds / 1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void showIms(String[] args) {
		int width = 352;
		int height = 288;
		int n = Integer.parseInt(args[1]);
		int mode = Integer.parseInt(args[2]);
		long milliseconds = Integer.parseInt(args[3]);
		DCT = new Color[height][width];
		decoder = new Color[height][width];

		img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		img1 = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		try {
			File file = new File(args[0]);
			InputStream is = new FileInputStream(file);

			long len = file.length();
			byte[] bytes = new byte[(int) len];

			int offset = 0;
			int numRead = 0;
			while (offset < bytes.length && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
				offset += numRead;
			}

			int ind = 0;
			for (int y = 0; y < height; y++) {

				for (int x = 0; x < width; x++) {

					byte a = 0;
					byte r = bytes[ind];
					byte g = bytes[ind + height * width];
					byte b = bytes[ind + height * width * 2];

					int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
					// int pix = ((a << 24) + (r << 16) + (g << 8) + b);
					img.setRGB(x, y, pix);
					ind++;

					Color color = new Color(r & 0xff, g & 0xff, b & 0xff);
					DCT[y][x] = color;
					decoder[y][x] = new Color();
				}
			}
			// Use labels to display the images
			frame = new JFrame();
			GridBagLayout gLayout = new GridBagLayout();
			frame.getContentPane().setLayout(gLayout);
			p= new JPanel(gLayout);

			JLabel lbText1 = new JLabel("Original image (Left)");
			lbText1.setHorizontalAlignment(SwingConstants.CENTER);
			JLabel lbText2 = new JLabel("Image after decoding (Right)");
			lbText2.setHorizontalAlignment(SwingConstants.CENTER);
			lbIm1 = new JLabel(new ImageIcon(img));
			lbIm2 = new JLabel(new ImageIcon(img1));

			con = new GridBagConstraints();
			con.fill = GridBagConstraints.HORIZONTAL;
			con.anchor = GridBagConstraints.CENTER;
			con.weightx = 0.5;
			con.gridx = 0;
			con.gridy = 0;
			p.add(lbText1, con);
			//frame.getContentPane().add(lbText1, c);

			con.fill = GridBagConstraints.HORIZONTAL;
			con.anchor = GridBagConstraints.CENTER;
			con.weightx = 0.5;
			con.gridx = 1;
			con.gridy = 0;
			p.add(lbText2, con);
			//frame.getContentPane().add(lbText2, c);

			con.fill = GridBagConstraints.HORIZONTAL;
			con.gridx = 0;
			con.gridy = 1;
//			frame.getContentPane().add(lbIm1, c);
			p.add(lbIm1, con);

			con.fill = GridBagConstraints.HORIZONTAL;
			con.gridx = 1;
			con.gridy = 1;
			//frame.getContentPane().add(lbIm2, c);
			p.add(lbIm2, con);
			
			frame.add(p);
			frame.pack();
			frame.setVisible(true);
			
			encoder(width, height, n);
			if (mode == 1) {
				sequential(width, height, n, milliseconds);
			} else if (mode == 2) {
				progressive(width, height, n, milliseconds);
			} else {
				progressiveBit(width, height, n, milliseconds);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		ImageReader ren = new ImageReader();
		ren.showIms(args);
	}

}


