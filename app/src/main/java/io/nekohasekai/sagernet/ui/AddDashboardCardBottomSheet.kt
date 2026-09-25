package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.nekohasekai.sagernet.databinding.ItemAddDashboardCardRowBinding
import io.nekohasekai.sagernet.databinding.LayoutAddDashboardCardSheetBinding

class AddDashboardCardBottomSheet(
    private val hiddenTypes: List<DashboardFragment.ItemType>,
    private val onCardAdded: (DashboardFragment.ItemType) -> Unit,
) : BottomSheetDialogFragment() {

    private var _binding: LayoutAddDashboardCardSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutAddDashboardCardSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.emptyText.isVisible = hiddenTypes.isEmpty()
        binding.addCardsRecycler.isVisible = hiddenTypes.isNotEmpty()

        binding.addCardsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.addCardsRecycler.adapter = AddCardsAdapter()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class AddCardsAdapter : RecyclerView.Adapter<AddCardsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val itemBinding = ItemAddDashboardCardRowBinding.inflate(inflater, parent, false)
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(hiddenTypes[position])
        }

        override fun getItemCount(): Int = hiddenTypes.size

        inner class ViewHolder(val itemBinding: ItemAddDashboardCardRowBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(type: DashboardFragment.ItemType) {
                itemBinding.cardTitle.setText(type.titleResId)
                itemBinding.cardDesc.setText(type.descResId)
                itemBinding.cardIcon.setImageResource(type.iconResId)

                itemBinding.btnAddCard.setOnClickListener {
                    onCardAdded(type)
                    dismiss()
                }
            }
        }
    }
}