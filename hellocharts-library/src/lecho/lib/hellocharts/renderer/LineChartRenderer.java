package lecho.lib.hellocharts.renderer;

import lecho.lib.hellocharts.Chart;
import lecho.lib.hellocharts.ChartCalculator;
import lecho.lib.hellocharts.LineChartDataProvider;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.util.CasteljauComputator;
import lecho.lib.hellocharts.util.Utils;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;

public class LineChartRenderer extends AbstractChartRenderer {
	private static final float LINE_SMOOTHNES = 0.16f;
	private static final int DEFAULT_LINE_STROKE_WIDTH_DP = 3;
	private static final int DEFAULT_TOUCH_TOLLERANCE_MARGIN_DP = 4;

	private static final int MODE_DRAW = 0;
	private static final int MODE_HIGHLIGHT = 1;

	private LineChartDataProvider dataProvider;

	private int touchTolleranceMargin;
	private Path linePath = new Path();
	private Paint linePaint = new Paint();
	private Paint pointPaint = new Paint();
	private RectF labelRect = new RectF();
	private PathCompat pathCompat = new PathCompat();

	public LineChartRenderer(Context context, Chart chart, LineChartDataProvider dataProvider) {
		super(context, chart);
		this.dataProvider = dataProvider;

		touchTolleranceMargin = Utils.dp2px(density, DEFAULT_TOUCH_TOLLERANCE_MARGIN_DP);

		linePaint.setAntiAlias(true);
		linePaint.setStyle(Paint.Style.STROKE);
		linePaint.setStrokeWidth(Utils.dp2px(density, DEFAULT_LINE_STROKE_WIDTH_DP));

		pointPaint.setAntiAlias(true);
		pointPaint.setStyle(Paint.Style.FILL);

	}

	@Override
	public void initMaxViewport() {
		calculateMaxViewport();
		chart.getChartCalculator().setMaxViewport(tempMaxViewport);
	}

	@Override
	public void initDimensions() {
		chart.getChartCalculator().setInternalMargin(calculateContentAreaMargin());
		labelPaint.setTextSize(Utils.sp2px(scaledDensity, chart.getChartData().getValueLabelTextSize()));
		labelPaint.getFontMetricsInt(fontMetrics);
	}

	@Override
	public void draw(Canvas canvas) {
		final LineChartData data = dataProvider.getLineChartData();
		for (Line line : data.getLines()) {
			if (line.hasLines()) {
				if (line.isSmooth()) {
					drawSmoothPath(canvas, line);
				} else {
					drawPath(canvas, line);
				}
			}
			linePath.reset();
			pathCompat.reset();
		}
	}

	@Override
	public void drawUnclipped(Canvas canvas) {
		final LineChartData data = dataProvider.getLineChartData();
		int lineIndex = 0;
		for (Line line : data.getLines()) {
			if (line.hasPoints()) {
				drawPoints(canvas, line, lineIndex, MODE_DRAW);
			}
			++lineIndex;
		}
		if (isTouched()) {
			// Redraw touched point to bring it to the front
			highlightPoints(canvas);
		}
	}

	@Override
	public boolean checkTouch(float touchX, float touchY) {
		oldSelectedValue.set(selectedValue);
		selectedValue.clear();
		final LineChartData data = dataProvider.getLineChartData();
		final ChartCalculator calculator = chart.getChartCalculator();
		int lineIndex = 0;
		for (Line line : data.getLines()) {
			int pointRadius = Utils.dp2px(density, line.getPointRadius());
			int valueIndex = 0;
			for (PointValue pointValue : line.getPoints()) {
				final float rawValueX = calculator.calculateRawX(pointValue.getX());
				final float rawValueY = calculator.calculateRawY(pointValue.getY());
				if (isInArea(rawValueX, rawValueY, touchX, touchY, pointRadius + touchTolleranceMargin)) {
					selectedValue.set(lineIndex, valueIndex);
				}
				++valueIndex;
			}
			++lineIndex;
		}
		// Check if touch is still on the same value, if not return false.
		if (oldSelectedValue.isSet() && selectedValue.isSet() && !oldSelectedValue.equals(selectedValue)) {
			return false;
		}
		return isTouched();
	}

	private void calculateMaxViewport() {
		tempMaxViewport.set(Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MAX_VALUE);
		LineChartData data = dataProvider.getLineChartData();

		// TODO: Optimize.
		for (Line line : data.getLines()) {
			// Calculate max and min for viewport.
			for (PointValue pointValue : line.getPoints()) {
				if (pointValue.getX() < tempMaxViewport.left) {
					tempMaxViewport.left = pointValue.getX();
				}
				if (pointValue.getX() > tempMaxViewport.right) {
					tempMaxViewport.right = pointValue.getX();
				}
				if (pointValue.getY() < tempMaxViewport.bottom) {
					tempMaxViewport.bottom = pointValue.getY();
				}
				if (pointValue.getY() > tempMaxViewport.top) {
					tempMaxViewport.top = pointValue.getY();
				}

			}
		}
	}

	private int calculateContentAreaMargin() {
		int contentAreaMargin = 0;
		final LineChartData data = dataProvider.getLineChartData();
		for (Line line : data.getLines()) {
			if (line.hasPoints()) {
				int margin = line.getPointRadius() + DEFAULT_TOUCH_TOLLERANCE_MARGIN_DP;
				if (margin > contentAreaMargin) {
					contentAreaMargin = margin;
				}
			}
		}
		return Utils.dp2px(density, contentAreaMargin);
	}

	private void drawPath(Canvas canvas, final Line line) {
		final ChartCalculator calculator = chart.getChartCalculator();
		int valueIndex = 0;
		linePaint.setStrokeWidth(Utils.dp2px(density, line.getStrokeWidth()));
		linePaint.setColor(line.getColor());
		for (PointValue pointValue : line.getPoints()) {
			final float rawX = calculator.calculateRawX(pointValue.getX());
			final float rawY = calculator.calculateRawY(pointValue.getY());
			if (valueIndex == 0) {
				// linePath.moveTo(rawX, rawY);
				pathCompat.moveTo(rawX, rawY);
			} else {
				// linePath.lineTo(rawX, rawY);
				pathCompat.lineTo(canvas, linePaint, rawX, rawY);
			}
			++valueIndex;
		}

		// canvas.drawPath(linePath, linePaint);
		pathCompat.drawPath(canvas, linePaint);
		if (line.isFilled()) {
			drawArea(canvas, line.getAreaTransparency());
		}
	}

	private void drawSmoothPath(Canvas canvas, final Line line) {
		final ChartCalculator calculator = chart.getChartCalculator();
		linePaint.setStrokeWidth(Utils.dp2px(density, line.getStrokeWidth()));
		linePaint.setColor(line.getColor());
		final int lineSize = line.getPoints().size();
		float previousPointX = Float.NaN;
		float previousPointY = Float.NaN;
		float currentPointX = Float.NaN;
		float currentPointY = Float.NaN;
		float nextPointX = Float.NaN;
		float nextPointY = Float.NaN;
		for (int valueIndex = 0; valueIndex < lineSize - 1; ++valueIndex) {
			if (Float.isNaN(currentPointX)) {
				PointValue linePoint = line.getPoints().get(valueIndex);
				currentPointX = calculator.calculateRawX(linePoint.getX());
				currentPointY = calculator.calculateRawY(linePoint.getY());
			}
			if (Float.isNaN(previousPointX)) {
				if (valueIndex > 0) {
					PointValue linePoint = line.getPoints().get(valueIndex - 1);
					previousPointX = calculator.calculateRawX(linePoint.getX());
					previousPointY = calculator.calculateRawY(linePoint.getY());
				} else {
					previousPointX = currentPointX;
					previousPointY = currentPointY;
				}
			}
			if (Float.isNaN(nextPointX)) {
				PointValue linePoint = line.getPoints().get(valueIndex + 1);
				nextPointX = calculator.calculateRawX(linePoint.getX());
				nextPointY = calculator.calculateRawY(linePoint.getY());
			}
			// afterNextPoint is always new one or it is equal nextPoint.
			final float afterNextPointX;
			final float afterNextPointY;
			if (valueIndex < lineSize - 2) {
				PointValue linePoint = line.getPoints().get(valueIndex + 2);
				afterNextPointX = calculator.calculateRawX(linePoint.getX());
				afterNextPointY = calculator.calculateRawY(linePoint.getY());
			} else {
				afterNextPointX = nextPointX;
				afterNextPointY = nextPointY;
			}
			// Calculate control points.
			final float firstDiffX = (nextPointX - previousPointX);
			final float firstDiffY = (nextPointY - previousPointY);
			final float secondDiffX = (afterNextPointX - currentPointX);
			final float secondDiffY = (afterNextPointY - currentPointY);
			final float firstControlPointX = currentPointX + (LINE_SMOOTHNES * firstDiffX);
			final float firstControlPointY = currentPointY + (LINE_SMOOTHNES * firstDiffY);
			final float secondControlPointX = nextPointX - (LINE_SMOOTHNES * secondDiffX);
			final float secondControlPointY = nextPointY - (LINE_SMOOTHNES * secondDiffY);
			// Move to start point.
			if (valueIndex == 0) {
				// linePath.moveTo(currentPointX, currentPointY);
				pathCompat.moveTo(currentPointX, currentPointY);
			}
			// linePath.cubicTo(firstControlPointX, firstControlPointY, secondControlPointX, secondControlPointY,
			// nextPointX, nextPointY);
			pathCompat.cubicTo(canvas, linePaint, firstControlPointX, firstControlPointY, secondControlPointX,
					secondControlPointY, nextPointX, nextPointY);
			// Shift values by one to prevent recalculation of values that have
			// been already calculated.
			previousPointX = currentPointX;
			previousPointY = currentPointY;
			currentPointX = nextPointX;
			currentPointY = nextPointY;
			nextPointX = afterNextPointX;
			nextPointY = afterNextPointY;
		}
		// canvas.drawPath(linePath, linePaint);
		pathCompat.drawPath(canvas, linePaint);
		if (line.isFilled()) {
			drawArea(canvas, line.getAreaTransparency());
		}
	}

	// TODO Drawing points can be done in the same loop as drawing lines but it
	// may cause problems in the future with
	// implementing point styles.
	private void drawPoints(Canvas canvas, Line line, int lineIndex, int mode) {
		final ChartCalculator calculator = chart.getChartCalculator();
		pointPaint.setColor(line.getColor());
		int valueIndex = 0;
		for (PointValue pointValue : line.getPoints()) {
			int pointRadius = Utils.dp2px(density, line.getPointRadius());
			final float rawX = calculator.calculateRawX(pointValue.getX());
			final float rawY = calculator.calculateRawY(pointValue.getY());
			if (calculator.isWithinContentRect((int) rawX, (int) rawY)) {
				// Draw points only if they are within contentRect
				if (MODE_DRAW == mode) {
					drawPoint(canvas, line, pointValue, rawX, rawY, pointRadius);
					if (line.hasLabels()) {
						drawLabel(canvas, calculator, line, pointValue, rawX, rawY, pointRadius + labelOffset);
					}
				} else if (MODE_HIGHLIGHT == mode) {
					highlightPoint(canvas, calculator, line, pointValue, rawX, rawY, lineIndex, valueIndex);
				} else {
					throw new IllegalStateException("Cannot process points in mode: " + mode);
				}
			}
			++valueIndex;
		}
	}

	private void drawPoint(Canvas canvas, Line line, PointValue pointValue, float rawX, float rawY, float pointRadius) {
		if (Line.SHAPE_SQUARE == line.getPointShape()) {
			canvas.drawRect(rawX - pointRadius, rawY - pointRadius, rawX + pointRadius, rawY + pointRadius, pointPaint);
		} else {
			canvas.drawCircle(rawX, rawY, pointRadius, pointPaint);
		}
	}

	private void highlightPoints(Canvas canvas) {
		int lineIndex = selectedValue.firstIndex;
		Line line = dataProvider.getLineChartData().getLines().get(lineIndex);
		drawPoints(canvas, line, lineIndex, MODE_HIGHLIGHT);
	}

	private void highlightPoint(Canvas canvas, ChartCalculator calculator, Line line, PointValue pointValue,
			float rawX, float rawY, int lineIndex, int valueIndex) {
		if (selectedValue.firstIndex == lineIndex && selectedValue.secondIndex == valueIndex) {
			int pointRadius = Utils.dp2px(density, line.getPointRadius());
			pointPaint.setColor(line.getDarkenColor());
			drawPoint(canvas, line, pointValue, rawX, rawY, pointRadius + touchTolleranceMargin);
			if (line.hasLabels() || line.hasLabelsOnlyForSelected()) {
				drawLabel(canvas, calculator, line, pointValue, rawX, rawY, pointRadius + labelOffset);
			}
		}
	}

	private void drawLabel(Canvas canvas, ChartCalculator calculator, Line line, PointValue pointValue, float rawX,
			float rawY, float offset) {
		final Rect contentRect = calculator.getContentRect();
		final int nummChars = line.getFormatter().formatValue(labelBuffer, pointValue.getY());
		final float labelWidth = labelPaint.measureText(labelBuffer, labelBuffer.length - nummChars, nummChars);
		final int labelHeight = Math.abs(fontMetrics.ascent);
		float left = rawX - labelWidth / 2 - labelMargin;
		float right = rawX + labelWidth / 2 + labelMargin;
		float top = rawY - offset - labelHeight - labelMargin * 2;
		float bottom = rawY - offset;
		if (top < contentRect.top) {
			top = rawY + offset;
			bottom = rawY + offset + labelHeight + labelMargin * 2;
		}
		if (left < contentRect.left) {
			left = rawX;
			right = rawX + labelWidth + labelMargin * 2;
		}
		if (right > contentRect.right) {
			left = rawX - labelWidth - labelMargin * 2;
			right = rawX;
		}
		labelRect.set(left, top, right, bottom);
		int orginColor = labelPaint.getColor();
		labelPaint.setColor(line.getDarkenColor());
		canvas.drawRect(left, top, right, bottom, labelPaint);
		labelPaint.setColor(orginColor);
		canvas.drawText(labelBuffer, labelBuffer.length - nummChars, nummChars, left + labelMargin, bottom
				- labelMargin, labelPaint);
	}

	private void drawArea(Canvas canvas, int transparency) {
		final ChartCalculator calculator = chart.getChartCalculator();
		linePath.lineTo(calculator.getContentRect().right, calculator.getContentRect().bottom);
		linePath.lineTo(calculator.getContentRect().left, calculator.getContentRect().bottom);
		linePath.close();
		linePaint.setStyle(Paint.Style.FILL);
		linePaint.setAlpha(transparency);
		// canvas.drawPath(linePath, linePaint);
		// pathCompat.drawAreaPath(canvas, linePaint);
		linePaint.setStyle(Paint.Style.STROKE);
	}

	private boolean isInArea(float x, float y, float touchX, float touchY, float radius) {
		float diffX = touchX - x;
		float diffY = touchY - y;
		return Math.pow(diffX, 2) + Math.pow(diffY, 2) <= 2 * Math.pow(radius, 2);
	}

	/**
	 * PathCompat uses Canvas.drawLines instead Canvas.drawPath. Supports normal lines and cubic Bezier's lines.
	 * Warning!: doesn't support breaks in line so line has to be continuous and doesn't support area chart.
	 * 
	 * @author Leszek Wach
	 * 
	 */
	public class PathCompat {
		private static final int DEFAULT_BUFFER_SIZE = 1024;

		/**
		 * Bufer for point coordinates to avoid calling drawLine for every line segment, instead call drawLines.
		 */
		private float[] buffer = new float[DEFAULT_BUFFER_SIZE];

		/**
		 * Number of points in buffer, index where put next line segment coordinate.
		 */
		private int bufferIndex = 0;

		/**
		 * De Casteljau's algorithm implementation to draw cubic Bezier's curves with hardware acceleration without
		 * using Path. For filling area Path still has to be used but it will be clipped to contentRect.
		 */
		private CasteljauComputator casteljauComputator = new CasteljauComputator();

		/**
		 * Buffer for cubic Bezier's curve points coordinate, four points(start point, end point, two control points),
		 * two coordinate each.
		 */
		private float[] bezierBuffer = new float[8];

		/**
		 * Computed bezier line point, as private member to avoid allocation.
		 */
		private PointF bezierOutPoint = new PointF();

		/**
		 * Step in pixels for drawing Bezier's curve
		 */
		private int pixelStep = 8;

		public void moveTo(float x, float y) {
			if (bufferIndex != 0) {
				// Move too only works for starting point.
				return;
			}
			buffer[bufferIndex++] = x;
			buffer[bufferIndex++] = y;
		}

		public void lineTo(Canvas canvas, Paint paint, float x, float y) {

			addLineToBuffer(x, y);

			drawLinesIfNeeded(canvas, paint);
		}

		private void drawLinesIfNeeded(Canvas canvas, Paint paint) {
			if (bufferIndex == buffer.length) {
				// Buffer full, draw lines and remember last point as the first point in buffer.
				canvas.drawLines(buffer, 0, bufferIndex, paint);
				final float lastX = buffer[bufferIndex - 2];
				final float lastY = buffer[bufferIndex - 1];
				bufferIndex = 0;
				buffer[bufferIndex++] = lastX;
				buffer[bufferIndex++] = lastY;
			}
		}

		private void addLineToBuffer(float x, float y) {
			if (bufferIndex == 0) {
				// No moveTo, set starting point to 0,0.
				buffer[bufferIndex++] = 0;
				buffer[bufferIndex++] = 0;
			}

			if (bufferIndex == 2) {
				// First segment.
				buffer[bufferIndex++] = x;
				buffer[bufferIndex++] = y;
			} else {
				final float lastX = buffer[bufferIndex - 2];
				final float lastY = buffer[bufferIndex - 1];
				buffer[bufferIndex++] = lastX;
				buffer[bufferIndex++] = lastY;
				buffer[bufferIndex++] = x;
				buffer[bufferIndex++] = y;
			}
		}

		public void cubicTo(Canvas canvas, Paint paint, float x1, float y1, float x2, float y2, float x3, float y3) {
			if (bufferIndex == 0) {
				// No moveTo, set starting point to 0,0.
				bezierBuffer[0] = 0;
				bezierBuffer[1] = 0;
			} else {
				bezierBuffer[0] = buffer[bufferIndex - 2];
				bezierBuffer[1] = buffer[bufferIndex - 1];
			}
			bezierBuffer[2] = x1;
			bezierBuffer[3] = y1;
			bezierBuffer[4] = x2;
			bezierBuffer[5] = y2;
			bezierBuffer[6] = x3;
			bezierBuffer[7] = y3;

			// First subline.
			addLineToBuffer(bezierBuffer[0], bezierBuffer[1]);
			drawLinesIfNeeded(canvas, paint);

			final float stepT = 1.0f / ((float) Math.abs((bezierBuffer[0] - x3)) / pixelStep);
			for (float t = stepT; t < 1.0f; t += stepT) {
				casteljauComputator.computePoint(t, bezierBuffer, bezierOutPoint);
				addLineToBuffer(bezierOutPoint.x, bezierOutPoint.y);
				drawLinesIfNeeded(canvas, paint);
			}

			// Last subline.
			addLineToBuffer(x3, y3);
			drawLinesIfNeeded(canvas, paint);
		}

		/**
		 * Resets internal state of PathCompat and prepare it to draw next line.
		 */
		public void reset() {
			bufferIndex = 0;
		}

		public void drawPath(Canvas canvas, Paint paint) {
			canvas.drawLines(buffer, 0, bufferIndex, paint);
			bufferIndex = 0;
		}

		public int getStep() {
			return pixelStep;
		}

		public void setStep(int step) {
			this.pixelStep = step;
		}

	}

}
