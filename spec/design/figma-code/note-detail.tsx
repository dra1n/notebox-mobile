// Figma get_design_context output for node 1502:483 ("notebox-note-detail"), saved 2026-10-03.
// React + Tailwind REFERENCE ONLY: read it for exact values (colors, sizes, paddings, fonts) and
// translate them to RN StyleSheet + notebox.ui.theme tokens. Asset URLs are replaced with the
// local copies in ../assets. The status bar is the device's and isn't something to implement.

const imgArrowLeft = "../assets/arrow-left.svg";
const imgLine = "../assets/divider-line.svg";

function BtnEdit({ className }: { className?: string }) {
  return (
    <div className={className || "h-[28px] relative w-[57px]"} data-node-id="0:1526" data-name="btn edit">
      <div className="absolute bg-[#ade4ea] inset-0 rounded-[3px]" data-node-id="0:1527" />
      <p className="absolute font-['Roboto:Medium'] font-medium left-[24.56%] right-[19.3%] text-[#323232] text-[14px] top-[calc(50%-8px)] tracking-[0.6px] whitespace-nowrap" data-node-id="0:1528">
        EDIT
      </p>
    </div>
  );
}

export default function NoteboxNoteDetail() {
  return (
    // phone frame: bg #f6f6f6, border #dfdfdf, radius 32 (mockup chrome only)
    <div className="bg-[#f6f6f6] flex flex-col items-start overflow-clip relative size-full" data-node-id="1502:483">
      {/* status-bar: bg #2c292b, h 44, pt 12, px 24; "9:41" Roboto SemiBold 14 #c6c6c6 */}
      <div className="flex flex-[1_0_0] flex-col items-start min-h-px relative w-full" data-node-id="1502:490">
        <div className="bg-[#2c292b] flex h-[64px] items-center justify-between px-[16px] relative shrink-0 w-full" data-node-id="1502:491" data-name="header">
          <div className="flex gap-[8px] items-center relative shrink-0" data-node-id="1502:492">
            <div className="relative shrink-0 size-[20px]" data-node-id="1502:745" data-name="arrow-left">
              <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgArrowLeft} />
            </div>
            <p className="font-['Roboto:Regular'] font-normal text-[#c6c6c6] text-[14px] whitespace-nowrap" data-node-id="1502:494">Back</p>
          </div>
          <p className="font-['Roboto:Regular'] font-normal max-w-[160px] overflow-hidden text-[#c6c6c6] text-[14px] text-ellipsis whitespace-nowrap" data-node-id="1502:495">
            Новое поколение дво...
          </p>
          <BtnEdit className="h-[28px] relative shrink-0 w-[57px]" />
        </div>
        <div className="bg-white flex flex-[1_0_0] flex-col gap-[16px] items-start min-h-px p-[20px] relative w-full" data-node-id="1502:499" data-name="reading-panel">
          <div className="flex items-center relative shrink-0" data-node-id="1502:500">
            <p className="font-['Roboto:Regular'] font-normal text-[#888] text-[13px] whitespace-nowrap" data-node-id="1502:501">
              Л. Н. Толстой. Анна Каренина
            </p>
          </div>
          <p className="font-['Roboto:SemiBold'] font-semibold min-w-full text-[#323232] text-[24px]" data-node-id="1502:502">
            Новое поколение дворянства
          </p>
          <div className="h-0 relative shrink-0 w-full" data-node-id="1502:503" data-name="Line">
            <div className="absolute inset-[-1px_0_0_0]">
              <img alt="" className="block max-w-none size-full" src={imgLine} />
            </div>
          </div>
          <div className="flex flex-col font-['Roboto:Regular'] font-normal gap-[12px] items-start text-[#323232] text-[16px] w-full" data-node-id="1502:504">
            {/* paragraphs: leading 24px, whitespace pre-wrap; gap 12 between paragraphs */}
            <p className="leading-[24px] w-full" data-node-id="1502:505">— Это новое поколение дворянства. …</p>
            <p className="leading-[24px] w-full" data-node-id="1502:506">Знаете, придется если вам пред домом …</p>
          </div>
          <div className="flex-[1_0_0] min-h-[40px] relative w-full" data-node-id="1502:507" /* spacer pushes tags to bottom */ />
          <div className="flex flex-col items-start relative shrink-0 w-full" data-node-id="1502:509">
            <div className="content-start flex flex-wrap gap-[8px] items-start w-full" data-node-id="1502:511">
              {/* tag chip: px 10, py 6, radius 4, Medium 13 #323232; bg #c3f0f5 (first) / #ade4ea (others) */}
              <div className="bg-[#c3f0f5] flex px-[10px] py-[6px] rounded-[4px]" data-node-id="1502:512">
                <p className="font-['Inter:Medium'] font-medium text-[#323232] text-[13px] whitespace-nowrap">классика</p>
              </div>
              <div className="bg-[#ade4ea] flex px-[10px] py-[6px] rounded-[4px]" data-node-id="1502:514">
                <p className="font-['Inter:Medium'] font-medium text-[#323232] text-[13px] whitespace-nowrap">прочитанное</p>
              </div>
              <div className="bg-[#ade4ea] flex px-[10px] py-[6px] rounded-[4px]" data-node-id="1502:516">
                <p className="font-['Inter:Medium'] font-medium text-[#323232] text-[13px] whitespace-nowrap">5+</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
