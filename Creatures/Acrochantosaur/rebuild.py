from pathlib import Path
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/"scripts"))
from build_dinosaurs import build
from validate_dinosaurs import validate
if __name__ == "__main__":
    build('Acrochantosaur')
    validate('Acrochantosaur')
