import os
from PIL import Image

def apply_alpha_mask(source_path, target_path):
    if not os.path.exists(source_path):
        print(f"Source file not found: {source_path}")
        return
    if not os.path.exists(target_path):
        print(f"Target file not found: {target_path}")
        return

    source_img = Image.open(source_path).convert("RGBA")
    target_img = Image.open(target_path).convert("RGBA")

    if source_img.size != target_img.size:
        print(f"Warning: Sizes do not match for {target_path}. Skipping.")
        return

    source_data = list(source_img.getdata())
    target_data = list(target_img.getdata())

    new_data = []
    for i in range(len(source_data)):
        # If source pixel is transparent, make target pixel transparent
        if source_data[i][3] == 0:
            new_data.append((0, 0, 0, 0))
        else:
            new_data.append(target_data[i])

    target_img.putdata(new_data)
    target_img.save(target_path)
    print(f"Applied alpha mask to {target_path}")

def main():
    # Base directory relative to project root
    base_dir = "src/main/resources/assets/vanillaplusadditions/textures/entity/cat_final/"
    
    # Path to the diamond armor which is the source of the alpha mask
    diamond_armor = os.path.join(base_dir, "cat_armor_diamond.png")
    
    other_armors = [
        "cat_armor_gold.png",
        "cat_armor_iron.png",
        "cat_armor_netherite.png"
    ]

    for armor in other_armors:
        target = os.path.join(base_dir, armor)
        apply_alpha_mask(diamond_armor, target)

if __name__ == "__main__":
    main()
