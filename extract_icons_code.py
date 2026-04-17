import re
import glob

def svg_to_compose(svg_content, name):
    # Extract path data
    paths = re.findall(r'<path.*?d="(.*?)".*?>', svg_content)
    if not paths:
        return ""
    
    # Determine the stroke vs fill to set alpha or specific style if needed in Compose
    # Though usually we just use pathBuilder.
    # Actually, simpler generation for Compose:
    
    lines = []
    lines.append(f"val {name}: ImageVector")
    lines.append(f"    get() = ImageVector.Builder(")
    lines.append(f"        name = \"{name}\",")
    lines.append(f"        defaultWidth = 24.dp,")
    lines.append(f"        defaultHeight = 24.dp,")
    lines.append(f"        viewportWidth = 24f,")
    lines.append(f"        viewportHeight = 24f")
    lines.append(f"    ).apply {{")
    
    for pd in paths:
        lines.append(f"        path(fill = SolidColor(Color.Black)) {{ // Auto-generated SolidColor, adjust if Outline")
        # Poor-man's svg path parser isn't ideal for complex paths. 
        # Compose 1.3+ supports `addPathNodes(pathData: String)` which is MUCH easier!
        lines[-1] = f"        path(fill = SolidColor(Color.Black)) {{ // Auto-generated SolidColor, adjust if Outline"
        
        lines.pop() # Wait, better to use addPathNodes in Jetpack Compose but we can't easily.
        # Let's just use `addPathNodes` via `ImageVector.Builder(name="...").apply { addPath(VectorPath(pathData = ...)) }` or similar.
    
    return ""

def generate_compose_icons(files):
    svgs = set()
    for f in files:
        with open(f, 'r') as file:
            content = file.read()
            # Simple match of <svg> tags
            matches = re.findall(r'(<svg[^>]*>[\s\S]*?</svg>)', content)
            for m in matches:
                # normalize whitespace to help deduplication
                norm = re.sub(r'\s+', ' ', m)
                svgs.add(norm)
    
    print(f"val StitchIcons = object {{")
    for i, svg in enumerate(svgs):
        print(f"    // Icon {i}")
        print(f"    // {svg}")
    print(f"}}")

html_files = glob.glob("*.html")
generate_compose_icons(html_files)
