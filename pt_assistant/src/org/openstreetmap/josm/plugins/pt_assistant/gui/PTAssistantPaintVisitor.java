package org.openstreetmap.josm.plugins.pt_assistant.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.util.HashMap;
import java.util.List;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.validation.PaintVisitor;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.plugins.pt_assistant.utils.RouteUtils;

public class PTAssistantPaintVisitor extends PaintVisitor {

	/** The graphics */
	private final Graphics g;
	/** The MapView */
	private final MapView mv;

	public PTAssistantPaintVisitor(Graphics g, MapView mv) {
		super(g, mv);
		this.g = g;
		this.mv = mv;
	}

	@Override
	public void visit(Relation r) {

		HashMap<Long, String> stopOrderMap = new HashMap<>();

		int stopCount = 1;

		for (RelationMember rm : r.getMembers()) {

			// if (rm.getMember().isIncomplete() && (rm.isNode() ||
			// rm.hasRole("stop") || rm.hasRole("stop_entry_only")
			// || rm.hasRole("stop_exit_only") || rm.hasRole("platform") ||
			// rm.hasRole("platform_entry_only")
			// || rm.hasRole("platform_exit_only"))) {
			//
			// if (stopOrderMap.containsKey(rm.getUniqueId())) {
			// label = stopOrderMap.get(rm.getUniqueId());
			// label = label + ";" + stopCount;
			// } else {
			//
			// }
			// }

			if (RouteUtils.isPTStop(rm) || (rm.getMember().isIncomplete() && (rm.isNode() || rm.hasRole("stop")
					|| rm.hasRole("stop_entry_only") || rm.hasRole("stop_exit_only") || rm.hasRole("platform")
					|| rm.hasRole("platform_entry_only") || rm.hasRole("platform_exit_only")))) {

				String label = "";

				if (stopOrderMap.containsKey(rm.getUniqueId())) {
					label = stopOrderMap.get(rm.getUniqueId());
					label = label + ";" + stopCount;
				} else {
					if (r.hasKey("ref")) {
						label = label + r.get("ref");
					} else if (r.hasKey("name")) {
						label = label + r.get("name");
					} else {
						label = "NA";
					}
					label = label + " - " + stopCount;
				}

				stopOrderMap.put(rm.getUniqueId(), label);
				drawStopLabel(rm.getMember(), label);
				if (!rm.getMember().isIncomplete()) {
					drawStop(rm.getMember(), label);
				}

				stopCount++;

			} else if (RouteUtils.isPTWay(rm)) {
				if (rm.isWay()) {
					visit(rm.getWay());
				} else if (rm.isRelation()) {
					visit(rm.getRelation());
				} else {
					// if the relation has members that do not fit with the
					// PT_Assistant data model, do nothing
				}
			} else {
				// if the relation has members that do not fit with the
				// PT_Assistant data model, do nothing
			}
		}

	}

	@Override
	public void visit(Way w) {

		if (w == null) {
			return;
		}

		/*-
		 * oneway values:
		 * 0 two-way street
		 * 1 oneway street in the way's direction
		 * 2 oneway street in ways's direction but public transport allowed
		 * -1 oneway street in reverse direction
		 * -2 oneway street in reverse direction but public transport allowed
		 */
		int oneway = 0;

		if (w.hasTag("junction", "roundabout") || w.hasTag("highway", "motorway")) {
			oneway = 1;
		} else if (w.hasTag("oneway", "1") || w.hasTag("oneway", "yes") || w.hasTag("oneway", "true")) {
			if (w.hasTag("busway", "lane") || w.hasTag("busway:left", "lane") || w.hasTag("busway:right", "lane")
					|| w.hasTag("oneway:bus", "no") || w.hasTag("busway", "opposite_lane")
					|| w.hasTag("oneway:psv", "no") || w.hasTag("trolley_wire", "backward")) {
				oneway = 2;
			} else {
				oneway = 1;
			}
		} else if (w.hasTag("oneway", "-1") || w.hasTag("oneway", "reverse")) {
			if (w.hasTag("busway", "lane") || w.hasTag("busway:left", "lane") || w.hasTag("busway:right", "lane")
					|| w.hasTag("oneway:bus", "no") || w.hasTag("busway", "opposite_lane")
					|| w.hasTag("oneway:psv", "no") || w.hasTag("trolley_wire", "backward")) {
				oneway = -2;
			} else {
				oneway = -1;
			}
		}

		visit(w.getNodes(), oneway);

	}

	public void visit(List<Node> nodes, int oneway) {
		Node lastN = null;
		for (Node n : nodes) {
			if (lastN == null) {
				lastN = n;
				continue;
			}
			this.drawSegment(lastN, n, new Color(208, 80, 208, 179), oneway);
			lastN = n;
		}
	}

	/**
	 * Draw a small rectangle. White if selected (as always) or red otherwise.
	 *
	 * @param n
	 *            The node to draw.
	 */
	@Override
	public void visit(Node n) {
		if (n.isDrawable() && isNodeVisible(n)) {
			drawNode(n, Color.BLUE);
		}
	}

	/**
	 * Draws a line around the segment
	 *
	 * @param n1
	 *            The first node of segment
	 * @param n2
	 *            The second node of segment
	 * @param color
	 *            The color
	 */
	protected void drawSegment(Node n1, Node n2, Color color, int oneway) {
		if (n1.isDrawable() && n2.isDrawable() && isSegmentVisible(n1, n2)) {
			drawSegment(mv.getPoint(n1), mv.getPoint(n2), color, oneway);
		}
	}

	/**
	 * Draws a line around the segment
	 *
	 * @param p1
	 *            The first point of segment
	 * @param p2
	 *            The second point of segment
	 * @param color
	 *            The color
	 */
	protected void drawSegment(Point p1, Point p2, Color color, int oneway) {

		double t = Math.atan2((double) p2.x - p1.x, (double) p2.y - p1.y);
		double cosT = 8 * Math.cos(t);
		double sinT = 8 * Math.sin(t);

		int[] xPoints = { (int) (p1.x + cosT), (int) (p2.x + cosT), (int) (p2.x - cosT), (int) (p1.x - cosT) };
		int[] yPoints = { (int) (p1.y - sinT), (int) (p2.y - sinT), (int) (p2.y + sinT), (int) (p1.y + sinT) };
		g.setColor(color);
		g.fillPolygon(xPoints, yPoints, 4);
		g.fillOval((int) (p1.x - 8), (int) (p1.y - 8), 16, 16);
		g.fillOval((int) (p2.x - 8), (int) (p2.y - 8), 16, 16);

		if (oneway != 0) {
			double middleX = (double) (p1.x + p2.x) / 2.0;
			double middleY = (double) (p1.y + p2.y) / 2.0;
			double cosTriangle = 6 * Math.cos(t);
			double sinTriangle = 6 * Math.sin(t);
			g.setColor(new Color(50, 50, 50));

			if (oneway > 0) {
				int[] xFillTriangle = { (int) (middleX + cosTriangle), (int) (middleX - cosTriangle),
						(int) (middleX + 2 * sinTriangle) };
				int[] yFillTriangle = { (int) (middleY - sinTriangle), (int) (middleY + sinTriangle),
						(int) (middleY + 2 * cosTriangle) };
				g.fillPolygon(xFillTriangle, yFillTriangle, 3);

				if (oneway == 2) {
					int[] xDrawTriangle = { (int) (middleX + cosTriangle), (int) (middleX - cosTriangle),
							(int) (middleX - 2 * sinTriangle) };
					int[] yDrawTriangle = { (int) (middleY - sinTriangle), (int) (middleY + sinTriangle),
							(int) (middleY - 2 * cosTriangle) };
					g.drawPolygon(xDrawTriangle, yDrawTriangle, 3);
				}
			}

			if (oneway < 0) {
				int[] xFillTriangle = { (int) (middleX + cosTriangle), (int) (middleX - cosTriangle),
						(int) (middleX - 2 * sinTriangle) };
				int[] yFillTriangle = { (int) (middleY - sinTriangle), (int) (middleY + sinTriangle),
						(int) (middleY - 2 * cosTriangle) };
				g.fillPolygon(xFillTriangle, yFillTriangle, 3);

				if (oneway == -2) {
					int[] xDrawTriangle = { (int) (middleX + cosTriangle), (int) (middleX - cosTriangle),
							(int) (middleX + 2 * sinTriangle) };
					int[] yDrawTriangle = { (int) (middleY - sinTriangle), (int) (middleY + sinTriangle),
							(int) (middleY + 2 * cosTriangle) };
					g.drawPolygon(xDrawTriangle, yDrawTriangle, 3);
				}
			}

		}

		// g.drawLine((int) (p1.x - cosT), (int) (p1.y - sinT), (int) (p2.x +
		// cosT), (int) (p2.y - sinT));
		// g.drawLine((int) (p1.x - cosT), (int) (p1.y + sinT), (int) (p2.x -
		// cosT), (int) (p2.y + sinT));

	}

	/**
	 * Draws a circle around the node
	 * 
	 * @param n
	 *            The node
	 * @param color
	 *            The circle color
	 */
	protected void drawNode(Node n, Color color) {

		Point p = mv.getPoint(n);

		g.setColor(color);
		g.drawOval(p.x - 5, p.y - 5, 10, 10);

	}

	protected void drawStop(OsmPrimitive primitive, String label) {

		// find the point to which the stop visualization will be linked:
		Node n = new Node(primitive.getBBox().getCenter());

		Point p = mv.getPoint(n);

		Color fillColor = null;

		if (primitive.hasTag("bus", "yes")) {
			fillColor = Color.BLUE;
		} else if (primitive.hasTag("tram", "yes")) {
			fillColor = Color.RED;
		} // TODO: add more options
		g.setColor(fillColor);

		if (primitive.hasTag("public_transport", "stop_position")) {
			g.fillOval(p.x - 8, p.y - 8, 16, 16);
		} else {
			g.fillRect(p.x - 8, p.y - 8, 16, 16);
		}

	}

	protected void drawStopLabel(OsmPrimitive primitive, String label) {

		// find the point to which the stop visualization will be linked:
		Node n = new Node(primitive.getBBox().getCenter());

		Point p = mv.getPoint(n);

		g.setColor(Color.WHITE);
		Font stringFont = new Font("SansSerif", Font.PLAIN, 24);
		g.setFont(stringFont);
		g.drawString(label, p.x + 20, p.y - 20);
		
		// get the parents of the primitive that are routes:
		String parentsLabel = "";
		for (OsmPrimitive parent: primitive.getReferrers()) {
			if (parent.getType().equals(OsmPrimitiveType.RELATION)) {
				Relation relation = (Relation) parent;
				if (RouteUtils.isTwoDirectionRoute(relation)) {
					parentsLabel = parentsLabel + relation.get("ref") + ";";
				}
			}
		}
		if (!parentsLabel.equals("")) {
			// remove the last semicolon:
			parentsLabel = parentsLabel.substring(0, parentsLabel.length()-1);
			g.setColor(new Color(200, 200, 200));
			Font parentLabelFont = new Font("SansSerif", Font.ITALIC, 20);
			g.setFont(parentLabelFont);
			g.drawString(parentsLabel, p.x + 20, p.y);
		}
		
	}
	
//	protected void drawStopParentLabel(OsmPrimitive primitive, String label) {
//		// find the point to which the stop visualization will be linked:
//		Node n = new Node(primitive.getBBox().getCenter());
//
//		Point p = mv.getPoint(n);
//
//		g.setColor(Color.WHITE);
//		Font stringFont = new Font("SansSerif", Font.PLAIN, 24);
//		g.setFont(stringFont);
//		g.drawString(label, p.x - 20, p.y - 20);
//	}

	protected Graphics getGraphics() {
		return this.g;
	}

}