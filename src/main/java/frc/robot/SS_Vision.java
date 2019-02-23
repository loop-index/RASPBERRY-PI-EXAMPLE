package frc.robot;

import java.util.ArrayList;
import java.util.List;


import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import edu.wpi.cscore.CvSink;
import edu.wpi.cscore.CvSource;
import edu.wpi.first.cameraserver.*;

/**
 *	Đây là code của bọn mài, đã gỡ ra khỏi thread và loop và cho vào method cho dễ gọi.
 */
public class SS_Vision{

	public Thread m_visionThread;
	double centerX = 0, centerY = 0;
	public double averageSize;
	boolean centered;
	public boolean paused;
	
	public double coeff = 1;
	public double olddistance = 0;
	
	//không dùng 
	public void vision() {
		m_visionThread = new Thread(() -> {
		});
		m_visionThread.setDaemon(true);
		m_visionThread.start();
	}

	CvSink cvSink = CameraServer.getInstance().getVideo();
	//chú ý cái tên của cái outputstream này ("RPi") để đặt cho đúng trong RPi dashboard
	CvSource outputStream = CameraServer.getInstance().putVideo("RPi", 320, 240);

	Mat mat = new Mat();
	Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));

	List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
	List<RotatedRect> boundRect = new ArrayList<RotatedRect>();
	List<Rect> boundingrect = new ArrayList<Rect>();
	
	Mat hierarchy = new Mat();

	//ồ lâu nay đặt tên ngu nó là HSV chứ không phải RGB các bạn tớ ạ =))
	int H = 75; 
	int S = 200; 
	int V = 200;
	int HE = 50; 
	int SE = 150; 
	int VE = 75;

	public double centerX_final = 0;
	public double centerY_final = 0;
	public double height = 0;
	public double width = 0;
	public double distance_final = 0;
	public double distance2 = 0;
	public double distance3 = 0;
	
	/*
	tại sao cái này phải return int:
	code cũ chạy trong loop nên có lệnh continue để skip phần còn lại khi cvsink không lấy được ảnh
	còn đây bỏ loop rồi nên cần có return 1 cái gì đấy nếu muốn skip phần còn lại
	*/
	public int process(){

		contours.removeAll(contours);
		boundRect.removeAll(boundRect);
		boundingrect.removeAll(boundingrect);

		if (cvSink.grabFrame(mat) == 0) {
			outputStream.notifyError(cvSink.getError());
			return 0;
		}

		Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2HSV);
		Core.inRange(mat, new Scalar(H - HE, S - SE, V - VE), new Scalar(H + HE, S + SE, V + VE), mat);
		Imgproc.dilate(mat, mat, kernel);
		Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);

		if (!paused){
		for (int i = 0; i < contours.size(); i++) {

			Rect newrect = Imgproc.boundingRect(contours.get(i));
			if (newrect.size().area() > 100){
				boundingrect.add(newrect);
			}

			MatOfPoint2f dst = new MatOfPoint2f();  
			contours.get(i).convertTo(dst, CvType.CV_32F);

			RotatedRect rect = Imgproc.minAreaRect(dst);
			if (rect.size.area() > 100){
					boundRect.add(rect);
					Imgproc.putText(mat, Math.round(rect.angle)+"", new Point(rect.center.x, rect.center.y-30), 0 , 0.5 , new Scalar(255,255,255));
					Imgproc.putText(mat, Math.round(rect.size.area())+"", new Point(rect.center.x, rect.center.y+50), 0 , 0.5 , new Scalar(255,255,255));
					Imgproc.putText(mat, Math.round(rect.center.x)+"", new Point(rect.center.x, rect.center.y+80), 0 , 0.5 , new Scalar(255,255,255));							
					// Imgproc.putText(mat, i+"", new Point(Imgproc.minAreaRect(dst).center.x,Imgproc.minAreaRect(dst).center.y-80), 0 , 0.5 , new Scalar(255,255,255));
				}
			}

		//sort by x
		for (int i = boundRect.size(); i > 0; i--){
			// Imgproc.putText(mat, i - 1 + "", new Point(boundRect.get(i - 1).center.x,boundRect.get(i - 1).center.y-50), 0 , 0.5 , new Scalar(255,255,255));
			
			for (int j = 0; j < i - 1; j++){
				if (boundRect.get(j).center.x > boundRect.get(j + 1).center.x){
					RotatedRect mid = boundRect.get(j);
					boundRect.set(j, boundRect.get(j + 1));
					boundRect.set(j + 1, mid);	
				}
				if (boundingrect.get(j).x > boundingrect.get(j+1).x){
					Rect middle = boundingrect.get(j);
					boundingrect.set(j, boundingrect.get(j + 1));
					boundingrect.set(j + 1, middle);
				}
			}
		}	

		for (int i = 0; i < boundRect.size() - 1; i++) {
				if (boundRect.get(i).angle < boundRect.get(i+1).angle && Math.round(boundRect.get(i).angle)!=-90.00) {
					centerX = Math.round((boundRect.get(i).center.x+boundRect.get(i+1).center.x)/2);
					centerY = Math.round((boundRect.get(i).center.y+boundRect.get(i+1).center.y)/2);
					if (Math.abs(320-centerX) < Math.abs(320-centerX_final)){
					centerX_final = centerX;
					centerY_final = centerY;
					height = boundingrect.get(i).height;
					width = boundingrect.get(i).width;
					// distance1 = 2.3/Math.tan(Math.toRadians(width*640/70.42));
					// distance2 = 15*70.42/(2*height*Math.tan(Math.toRadians(70.42)));
					distance3 = (6.5 * 640) / (2 * height * Math.tan(70.42/2)) * 2.54;
					distance2 = 7000/height;
					distance_final = distance2 + (distance2-distance3)*2.45;
					}

					Imgproc.putText(mat, i+"", new Point(boundingrect.get(i).x, boundingrect.get(i).y-100), 0, 0.5, new Scalar(255,255,255));
					Imgproc.putText(mat, "*", new Point(centerX_final,centerY_final), 0 , 0.5 , new Scalar(255,255,255));
					centered = true;
					averageSize = (boundRect.get(i).size.area() + boundRect.get(i + 1).size.area())/2;
				}
				if (!centered){
					averageSize = -1;
				}
		}
		}
		else {
			averageSize = -1;
		}
		outputStream.putFrame(mat);
		return 1;
		}
	}
	
