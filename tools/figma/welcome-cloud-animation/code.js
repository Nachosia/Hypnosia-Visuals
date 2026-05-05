const SOURCE_NODE_ID = "496:450";
const SOURCE_FRAME_NAME = "accaut Welcome";
const GENERATED_PREFIX = "accaut Welcome / ";
const FRAME_GAP = 42;

const WHITE = { r: 1, g: 1, b: 1 };
const MUTED_WHITE = { r: 0.82, g: 0.82, b: 0.86 };

function solid(color, opacity = 1) {
  return [{ type: "SOLID", color, opacity }];
}

async function loadFont() {
  const candidates = [
    { family: "Inter", style: "Medium" },
    { family: "Inter", style: "Regular" },
    { family: "Roboto", style: "Medium" },
    { family: "Arial", style: "Regular" },
  ];

  for (const font of candidates) {
    try {
      await figma.loadFontAsync(font);
      return font;
    } catch (_) {
      // Try the next installed font.
    }
  }

  throw new Error("No usable font found. Install Inter or change the font list in code.js.");
}

async function findSourceFrame() {
  const byId = await figma.getNodeByIdAsync(SOURCE_NODE_ID);
  if (byId && byId.type === "FRAME") {
    return byId;
  }

  const byName = figma.currentPage.findOne((node) => node.type === "FRAME" && node.name === SOURCE_FRAME_NAME);
  if (byName && byName.type === "FRAME") {
    return byName;
  }

  return null;
}

function removeGeneratedFrames(parent) {
  for (const child of [...parent.children]) {
    if (child.name.startsWith(GENERATED_PREFIX)) {
      child.remove();
    }
  }
}

function findCloud(frame) {
  const node = frame.findOne((child) => child.name === "cloud" || child.name === "cloud-source");
  if (!node) {
    throw new Error(`Cloud layer not found in ${frame.name}`);
  }
  node.name = "cloud-source";
  return node;
}

function setNodeGeometry(node, x, y, width, height, opacity = 1) {
  node.x = x;
  node.y = y;
  if ("resize" in node) {
    node.resize(width, height);
  }
  node.opacity = opacity;
}

function createTail(frame, x, y, width, rotation, opacity) {
  const tail = figma.createRectangle();
  tail.name = "cloud-tail-to-w";
  tail.x = x;
  tail.y = y;
  tail.resize(width, 4);
  tail.rotation = rotation;
  tail.cornerRadius = 2;
  tail.fills = solid(WHITE, opacity);
  tail.opacity = opacity;
  frame.appendChild(tail);
  return tail;
}

function createText(frame, font, text, x, y, width, height, opacity, size = 48) {
  const node = figma.createText();
  node.name = "welcome-cloud-text";
  node.fontName = font;
  node.characters = text;
  node.x = x;
  node.y = y;
  node.resize(width, height);
  node.fontSize = size;
  node.lineHeight = { unit: "PIXELS", value: size * 1.15 };
  node.letterSpacing = { unit: "PIXELS", value: 0 };
  node.fills = solid(WHITE, opacity);
  node.opacity = opacity;
  frame.appendChild(node);
  return node;
}

function createGhostText(frame, font, x, y, width, height) {
  return createText(frame, font, "Welcome Cloud", x, y, width, height, 0, 48);
}

function cloneStage(source, index, name) {
  const frame = source.clone();
  frame.name = `${GENERATED_PREFIX}${name}`;
  frame.x = source.x + index * (source.width + FRAME_GAP);
  frame.y = source.y + source.height + 60;
  frame.clipsContent = true;
  return frame;
}

function setPrototype(from, to, timeout, duration, easingType) {
  try {
    from.reactions = [
      {
        trigger: { type: "AFTER_TIMEOUT", timeout },
        action: {
          type: "NODE",
          destinationId: to.id,
          navigation: "NAVIGATE",
          transition: {
            type: "SMART_ANIMATE",
            easing: { type: easingType },
            duration,
            matchLayers: true,
          },
        },
      },
    ];
    return true;
  } catch (_) {
    return false;
  }
}

async function main() {
  const source = await findSourceFrame();
  if (!source || source.type !== "FRAME") {
    throw new Error(`Source frame ${SOURCE_NODE_ID} / "${SOURCE_FRAME_NAME}" was not found or is not a frame.`);
  }

  const parent = source.parent;
  if (!parent || !("children" in parent)) {
    throw new Error("Source frame parent is not writable.");
  }

  const font = await loadFont();
  removeGeneratedFrames(parent);

  const stage1 = cloneStage(source, 0, "01 cloud idle");
  const stage2 = cloneStage(source, 1, "02 cloud split");
  const stage3 = cloneStage(source, 2, "03 text forming");
  const stage4 = cloneStage(source, 3, "04 final Welcome Cloud");

  const stages = [stage1, stage2, stage3, stage4];
  for (const stage of stages) {
    // Stable helper layers let Smart Animate interpolate instead of popping.
    createTail(stage, 340, 184, 1, -8, 0);
    createGhostText(stage, font, 224, 144, 310, 66);
  }

  setNodeGeometry(findCloud(stage1), 184, 68, 256, 256, 1);
  setNodeGeometry(stage1.findOne((n) => n.name === "cloud-tail-to-w"), 348, 183, 1, 0, 0);

  setNodeGeometry(findCloud(stage2), 170, 78, 236, 236, 1);
  setNodeGeometry(stage2.findOne((n) => n.name === "cloud-tail-to-w"), 346, 170, 82, 4, 1);
  stage2.findOne((n) => n.name === "cloud-tail-to-w").rotation = -18;
  const stage2Text = stage2.findOne((n) => n.name === "welcome-cloud-text");
  stage2Text.characters = "W";
  stage2Text.x = 390;
  stage2Text.y = 145;
  stage2Text.opacity = 0.24;
  stage2Text.fills = solid(MUTED_WHITE, 0.24);

  setNodeGeometry(findCloud(stage3), 124, 96, 168, 168, 0.78);
  setNodeGeometry(stage3.findOne((n) => n.name === "cloud-tail-to-w"), 262, 172, 112, 4, 1);
  stage3.findOne((n) => n.name === "cloud-tail-to-w").rotation = -8;
  const stage3Text = stage3.findOne((n) => n.name === "welcome-cloud-text");
  stage3Text.characters = "Welcome";
  stage3Text.x = 300;
  stage3Text.y = 145;
  stage3Text.opacity = 0.68;
  stage3Text.fills = solid(WHITE, 0.68);

  setNodeGeometry(findCloud(stage4), 108, 112, 112, 112, 1);
  setNodeGeometry(stage4.findOne((n) => n.name === "cloud-tail-to-w"), 212, 181, 42, 4, 0.85);
  stage4.findOne((n) => n.name === "cloud-tail-to-w").rotation = 0;
  const stage4Text = stage4.findOne((n) => n.name === "welcome-cloud-text");
  stage4Text.characters = "Welcome Cloud";
  stage4Text.x = 246;
  stage4Text.y = 145;
  stage4Text.opacity = 1;
  stage4Text.fills = solid(WHITE, 1);

  const reactionsApplied = [
    setPrototype(stage1, stage2, 0.25, 0.5, "EASE_OUT"),
    setPrototype(stage2, stage3, 0.05, 0.7, "EASE_IN_AND_OUT"),
    setPrototype(stage3, stage4, 0.05, 0.5, "EASE_OUT"),
  ];

  figma.currentPage.selection = stages;
  figma.viewport.scrollAndZoomIntoView(stages);

  figma.closePlugin(
    `Created ${stages.length} Welcome Cloud animation frames. Prototype reactions: ${
      reactionsApplied.every(Boolean) ? "applied" : "not supported by this Figma runtime"
    }.`
  );
}

main().catch((error) => {
  figma.closePlugin(`Hypnosia Welcome Cloud Animator failed: ${error.message}`);
});
