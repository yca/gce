package gazi.egitim.osman;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * GraphView creates a scaled line or bar graph with x and y axis labels. 
 * @author Arno den Hond
 *
 */
public class GraphView extends View {

	public static boolean BAR = true;
	public static boolean LINE = false;

	private Paint paint;
	private float[] values;
	private String[] horlabels;
	private String[] verlabels;
	private String title;
	private boolean type;
	private GestureDetector mGesture;
	private ScaleGestureDetector mScaleDetector;
	private float mScaleFactor = 1;
	private float currentYOffset = 0;
	
	private boolean autoScale = false;
	float largest = Integer.MIN_VALUE;
	float smallest = Integer.MAX_VALUE;

	public float[] getCurrentValues() {
		return values;
	}
	
	public void setAutoScale(boolean auto) {
		autoScale = auto;
		largest = Integer.MIN_VALUE;
		smallest = Integer.MAX_VALUE;
	}

	public GraphView(Context context, float[] values, String title, String[] horlabels, String[] verlabels, boolean type) {
		super(context);
		if (values == null)
			values = new float[0];
		else
			this.values = values;
		if (title == null)
			title = "";
		else
			this.title = title;
		if (horlabels == null)
			this.horlabels = new String[0];
		else
			this.horlabels = horlabels;
		if (verlabels == null)
			this.verlabels = new String[0];
		else
			this.verlabels = verlabels;
		this.type = type;
		paint = new Paint();
		
		mGesture = new GestureDetector(new GraphGestureListener());
		mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());

	}
	
	private class GraphGestureListener extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onDown(MotionEvent e) {
			Log.v("graph", "down");
			return super.onDown(e);
			
		}
		
		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2,
				float distanceX, float distanceY) {
			Log.v("graph", "scroll: x=" + distanceX + " y=" + distanceY);
			float range = getMax() - getMin();
			
			scrollYAxis(distanceY * range / 32768 * 50);
			invalidate();
			return super.onScroll(e1, e2, distanceX, distanceY);
		}
		
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			Log.v("graph", "fling");
			return super.onFling(e1, e2, velocityX, velocityY);
		}
		
		@Override
		public boolean onDoubleTap(MotionEvent e) {
			Log.v("graph", "dtap");
			mScaleFactor = 1;
			currentYOffset = 0;
			setAutoScale(autoScale);
			setYAxis();
			invalidate();
			return super.onDoubleTap(e);
		}
	}
	
	private void scrollYAxis(float offset) {
		currentYOffset  += offset;
		setYAxis();
	}
	
	private void setYAxis() {
		float max = getMax();
		float min = getMin();
		verlabels[0] = Float.toString(max);
        verlabels[verlabels.length - 1] = Float.toString(min);
	}
	
	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		@Override
	    public boolean onScale(ScaleGestureDetector detector) {
			float fact = detector.getScaleFactor();
	        mScaleFactor *= fact * fact;
	        
	        // Don't let the object get too small or too large.
	        mScaleFactor = Math.max(1f, Math.min(mScaleFactor, 32768f));
	        
	        setYAxis();
	        invalidate();
	        return true;
	    }
	}
	
	public void addData(float[] data) {
		if (data.length < values.length) {
			System.arraycopy(values, data.length, values, 0, values.length - data.length);
			System.arraycopy(data, 0, values, values.length - data.length, data.length);
		} else {
			System.arraycopy(data, 0, values, 0, values.length);
		}
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		float border = 20;
		float horstart = border * 2;
		float height = getHeight();
		float width = getWidth() - 1;
		float max = getMax();
		float min = getMin();
		float diff = max - min;
		float graphheight = height - (2 * border);
		float graphwidth = width - (2 * border);

		paint.setTextAlign(Align.LEFT);
		int vers = verlabels.length - 1;
		for (int i = 0; i < verlabels.length; i++) {
			paint.setColor(Color.DKGRAY);
			float y = ((graphheight / vers) * i) + border;
			canvas.drawLine(horstart, y, width, y, paint);
			paint.setColor(Color.WHITE);
			canvas.drawText(verlabels[i], 0, y, paint);
		}
		int hors = horlabels.length - 1;
		for (int i = 0; i < horlabels.length; i++) {
			paint.setColor(Color.DKGRAY);
			float x = ((graphwidth / hors) * i) + horstart;
			canvas.drawLine(x, height - border, x, border, paint);
			paint.setTextAlign(Align.CENTER);
			if (i==horlabels.length-1)
				paint.setTextAlign(Align.RIGHT);
			if (i==0)
				paint.setTextAlign(Align.LEFT);
			paint.setColor(Color.WHITE);
			canvas.drawText(horlabels[i], x, height - 4, paint);
		}

		paint.setTextAlign(Align.CENTER);
		canvas.drawText(title, (graphwidth / 2) + horstart, border - 4, paint);

		if (max != min) {
			paint.setColor(Color.LTGRAY);
			if (type == BAR) {
				float datalength = values.length;
				float colwidth = (width - (2 * border)) / datalength;
				for (int i = 0; i < values.length; i++) {
					if (values[i] > largest) {
						largest = values[i];
						setYAxis();
					}
					if (values[i] < smallest) {
						smallest = values[i];
						setYAxis();
					}
					float val = values[i] - min;
					float rat = val / diff;
					float h = graphheight * rat;
					canvas.drawRect((i * colwidth) + horstart, (border - h) + graphheight, ((i * colwidth) + horstart) + (colwidth - 1), height - (border - 1), paint);
				}
			} else {
				float datalength = values.length;
				float colwidth = (width - (2 * border)) / datalength;
				float halfcol = colwidth / 2;
				float lasth = 0;
				for (int i = 0; i < values.length; i++) {
					if (values[i] > largest) {
						largest = values[i];
						setYAxis();
					}
					if (values[i] < smallest) {
						smallest = values[i];
						setYAxis();
					}
					float val = values[i] - min;
					float rat = val / diff;
					float h = graphheight * rat;
					if (i > 0)
						canvas.drawLine(((i - 1) * colwidth) + (horstart + 1) + halfcol, (border - lasth) + graphheight, (i * colwidth) + (horstart + 1) + halfcol, (border - h) + graphheight, paint);
					lasth = h;
				}
			}
		}
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
	    // Let the ScaleGestureDetector inspect all events.
	    mScaleDetector.onTouchEvent(ev);
	    if (!mScaleDetector.isInProgress())
	    	mGesture.onTouchEvent(ev);
	    return true;
	}

	private float getMax() {
		if (!autoScale)
			return 32768 / mScaleFactor - currentYOffset;
		
		/*float largest = Integer.MIN_VALUE;
		for (int i = 0; i < values.length; i++)
			if (values[i] > largest)
				largest = values[i];*/
		return largest;
	}

	private float getMin() {
		/*float smallest = Integer.MAX_VALUE;
		for (int i = 0; i < values.length; i++)
			if (values[i] < smallest)
				smallest = values[i];*/
		if (!autoScale)
			return -32768 / mScaleFactor - currentYOffset;
		
		return smallest;
	}

}
